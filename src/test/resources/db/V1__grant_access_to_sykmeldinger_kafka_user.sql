DO
$$
BEGIN
        IF EXISTS
            (SELECT 1 from pg_roles where rolname = 'sykmeldinger-kafka-user')
        THEN
            GRANT SELECT ON ALL TABLES IN SCHEMA public TO "sykmeldinger-kafka-user";
            ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO "sykmeldinger-kafka-user";
END IF;
END
$$;
