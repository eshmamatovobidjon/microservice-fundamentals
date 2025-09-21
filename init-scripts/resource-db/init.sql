CREATE TABLE IF NOT EXISTS resources (
    id SERIAL PRIMARY KEY,
    s3url VARCHAR(255),
    original_file_name VARCHAR(255),
    uploaded_at TIMESTAMP
);