CREATE TABLE IF NOT EXISTS storages (
    id SERIAL PRIMARY KEY,
    storage_type VARCHAR(50) NOT NULL,
    bucket VARCHAR(255) NOT NULL,
    path VARCHAR(255) NOT NULL
);

INSERT INTO storages (storage_type, bucket, path) VALUES
('STAGING', 'my-app-mp3-resources', '/staging'),
('PERMANENT', 'my-app-mp3-resources', '/permanent');
