# =============================================================
#  backend/Dockerfile
#  Author : Kanishk Sandilya
# =============================================================

FROM python:3.11-slim

WORKDIR /app

# Install system deps needed by psycopg2 (Postgres driver)
RUN apt-get update && apt-get install -y --no-install-recommends \
    libpq-dev gcc curl \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# Copy requirements first (layer cache trick)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# Non-root user for security
RUN addgroup --system app && adduser --system --ingroup app app
USER app

EXPOSE 5000

CMD ["gunicorn", "--bind", "0.0.0.0:5000", "--workers", "2", "app:app"]
