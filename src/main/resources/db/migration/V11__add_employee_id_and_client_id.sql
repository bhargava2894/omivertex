-- Flyway migration V11: Add unique business identifiers
ALTER TABLE public.associates ADD COLUMN employee_id character varying(255);
ALTER TABLE public.associates ADD CONSTRAINT uk_associates_employee_id UNIQUE (employee_id);

ALTER TABLE public.clients ADD COLUMN client_id character varying(255);
ALTER TABLE public.clients ADD CONSTRAINT uk_clients_client_id UNIQUE (client_id);
