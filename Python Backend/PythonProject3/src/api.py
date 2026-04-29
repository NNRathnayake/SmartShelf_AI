from flask import Flask, jsonify, request
from predict import predict_sales
import pandas as pd
import os

app = Flask(__name__)


BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_PATH = os.path.join(BASE_DIR, "data", "last_known_data.csv")

df = pd.read_csv(DATA_PATH, parse_dates=["date"])



@app.route("/", methods=["GET"])
def home():
    return jsonify({
        "status": "running",
        "message": "Smart Shelf AI API is active "
    })



@app.route("/predict", methods=["POST"])
def predict():
    data = request.get_json()

    result = predict_sales(data)

    return jsonify({
        "predicted_sales": round(float(result), 2)
    })



@app.route("/sales-history", methods=["GET"])
def sales_history():


    store_nbr = request.args.get("store_nbr", default=1, type=int)
    family    = request.args.get("family", default="BEVERAGES", type=str)

    filtered = df[
        (df["store_nbr"] == store_nbr) &
        (df["family"] == family)
    ].sort_values("date")

    # Build clean time-series
    data = [
        {
            "date": row["date"].strftime("%Y-%m-%d"),
            "sales": float(round(row["sales"], 2))
        }
        for _, row in filtered.iterrows()
    ]

    return jsonify({
        "store_nbr": store_nbr,
        "family": family,
        "data": data
    })



@app.route("/forecast", methods=["GET"])
def forecast():

    store_nbr = request.args.get("store_nbr", default=1, type=int)
    family    = request.args.get("family", default="BEVERAGES", type=str)
    days      = request.args.get("days", default=7, type=int)
    promotion = request.args.get("onpromotion", default=0, type=int)

    from datetime import date, timedelta

    today = date.today()
    results = []

    for i in range(1, days + 1):
        future_date = (today + timedelta(days=i)).strftime("%Y-%m-%d")

        user_input = {
            "store_nbr": store_nbr,
            "family": family,
            "date": future_date,
            "onpromotion": promotion
        }

        predicted = predict_sales(user_input)

        results.append({
            "date": future_date,
            "predicted_sales": round(float(predicted), 2)
        })

    return jsonify({
        "store_nbr": store_nbr,
        "family": family,
        "forecast": results
    })



if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)