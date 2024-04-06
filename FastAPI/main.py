from datetime import datetime
from fastapi import FastAPI, Form
import httpx
import pycountry
from fastapi.responses import HTMLResponse

app = FastAPI()

OPENAQ_API_KEY = "key_number"


async def fetch_data(country_code, pollutant, start_date, end_date):
    if end_date.date() == datetime.now().date():
        url = f"https://api.openaq.org/v2/measurements?country={country_code}&date_from={start_date}" \
              f"&parameter={pollutant}&limit=1000"
    else:
        url = f"https://api.openaq.org/v2/measurements?country={country_code}&date_from={start_date}" \
              f"&date_to={end_date}&parameter={pollutant}&limit=1000"
    async with httpx.AsyncClient() as client:
        response = await client.get(url, headers={"X-API-Key": OPENAQ_API_KEY})
        data = response.json()
    return data.get('results', [])


def process_results(results):
    values = [result.get('value', 0) for result in results]
    locations = [(result.get('location'), result.get('value')) for result in results]

    max_value = round(max(values), 2)
    mean_value = round(sum(values) / len(values), 2)
    location_max = max(locations, key=lambda x: x[1])[0]
    return mean_value, max_value, location_max


@app.get("/", response_class=HTMLResponse)
async def root():
    with open("index.html", "r", encoding="utf-8") as file:
        content = file.read()
    return HTMLResponse(content=content, status_code=200)


@app.post("/submit", response_class=HTMLResponse)
async def submit(first_country: str = Form(), second_country: str = Form(), pollutant: str = Form(),
                 start_date: datetime = Form(), end_date: datetime = Form()):
    first_country_code = pycountry.countries.get(name=first_country)
    second_country_code = pycountry.countries.get(name=second_country)
    if first_country_code is None:
        return HTMLResponse(content="<body>Country {first_country} doesn't exist.</body>".format(
            first_country=first_country), status_code=400)
    if second_country_code is None:
        return HTMLResponse(content="<body>Country {second_country} doesn't exist.</body>".format(
            second_country=second_country), status_code=400)
    first_country_code = first_country_code.alpha_2
    second_country_code = second_country_code.alpha_2
    if start_date.date() > datetime.now().date() or end_date.date() > datetime.now().date() or end_date < start_date:
        return HTMLResponse(content="<body>Incorrect dates.</body>", status_code=400)
    try:
        results_1 = await fetch_data(first_country_code, pollutant, start_date, end_date)
        results_2 = await fetch_data(second_country_code, pollutant, start_date, end_date)
        if len(results_1) == 0 and len(results_2) == 0:
            return HTMLResponse(content="<body>No data found for those parameters.</body>", status_code=400)
        elif len(results_1) == 0:
            unit = results_2[0].get('unit')
            mean_value, max_value, location_max = process_results(results_2)
            html_response = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <title>Results</title>
                    </head>
                    <body>
                        <h1>Results for {second_country}</h1>
                        <p>Mean value: {mean_value} {unit}</p>
                        <p>Maximum value: {max_value} {unit} for location {max_location}</p>
                        <br>
                        <p>No data found for {first_country}.<p>
                    </body>
                    </html>
                    """.format(first_country=first_country, unit=unit, mean_value=mean_value,
                               max_value=max_value, max_location=location_max, second_country=second_country)
        elif len(results_2) == 0:
            unit = results_1[0].get('unit')
            mean_value, max_value, location_max = process_results(results_1)
            html_response = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <title>Results</title>
                    </head>
                    <body>
                        <h1>Results for {first_country}</h1>
                        <p>Mean value: {mean_value} {unit}</p>
                        <p>Maximum value: {max_value} {unit} for location {max_location}</p>
                        <br>
                        <p>No data found for {second_country}.<p>
                    </body>
                    </html>
                    """.format(first_country=first_country, unit=unit, mean_value=mean_value,
                               max_value=max_value, max_location=location_max,
                               second_country=second_country)
        else:
            unit = results_1[0].get('unit')
            mean_value_1, max_value_1, location_max_1 = process_results(results_1)
            mean_value_2, max_value_2, location_max_2 = process_results(results_2)
            if mean_value_1 < mean_value_2:
                verdict = f"worse in {second_country}"
            elif mean_value_1 > mean_value_2:
                verdict = f"worse in {first_country}"
            else:
                verdict = "the same"
            html_response = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <title>Results</title>
                    </head>
                    <body>
                        <h1>Results for {first_country}</h1>
                        <p>Mean value: {mean_value_1} {unit}</p>
                        <p>Maximum value: {max_value_1} {unit} for location {max_location_1}</p>
                        <br>
                        <h1>Results for {second_country}</h1>
                        <p>Mean value: {mean_value_2} {unit}</p>
                        <p>Maximum value: {max_value_2} {unit} for location {max_location_2}</p>
                        <br>
                        <p>Mean value was {verdict}.<p>
                    </body>
                    </html>
                    """.format(first_country=first_country, unit=unit, mean_value_1=mean_value_1,
                               max_value_1=max_value_1, second_country=second_country, max_location_1=location_max_1,
                               mean_value_2=mean_value_2, max_value_2=max_value_2, max_location_2=location_max_2,
                               verdict=verdict)

        return HTMLResponse(content=html_response, status_code=200)

    except Exception as e:
        return HTMLResponse(content="<body>Error connecting to server.</body>", status_code=500)
