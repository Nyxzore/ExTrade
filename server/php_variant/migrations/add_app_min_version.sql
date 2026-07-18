INSERT INTO versions (object, version) VALUES ('app_min_version', '1')
ON CONFLICT (object) DO NOTHING;
