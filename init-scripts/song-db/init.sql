CREATE TABLE IF NOT EXISTS songs (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    artist VARCHAR(100) NOT NULL,
    album VARCHAR(100),
    duration VARCHAR(5),
    year VARCHAR(4)
);