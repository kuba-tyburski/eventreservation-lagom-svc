-- Drop table

-- DROP TABLE public.customer_reservation_report;

CREATE TABLE public.customer_reservation_report (
	reservation_id varchar NOT NULL,
	event_id varchar NOT NULL,
	customer_id varchar NOT NULL,
	ordered int4 NULL,
	reserved int4 NOT NULL,
	status varchar NOT NULL,
	reserved_at timestamp NOT NULL,
	cancelled_at timestamp NULL,
	CONSTRAINT customer_reservation_report_pkey PRIMARY KEY (reservation_id)
);
CREATE INDEX idx__customer_reservation_report__customer_id ON public.customer_reservation_report USING btree (customer_id);
CREATE INDEX idx__customer_reservation_report__event_id ON public.customer_reservation_report USING btree (event_id);
CREATE INDEX idx__customer_reservation_report__event_id__customer_id ON public.customer_reservation_report USING btree (event_id, customer_id);
CREATE INDEX idx__customer_reservation_report__status ON public.customer_reservation_report USING btree (status);

-- Drop table

-- DROP TABLE public.journal;

CREATE TABLE public.journal (
	"ordering" bigserial NOT NULL,
	deleted bool NOT NULL DEFAULT false,
	persistence_id varchar(255) NOT NULL,
	sequence_number int8 NOT NULL,
	message bytea NOT NULL,
	tags varchar(255) NULL,
	CONSTRAINT journal_pk PRIMARY KEY (persistence_id, sequence_number)
);
CREATE UNIQUE INDEX journal_ordering_idx ON public.journal USING btree (ordering);

-- Drop table

-- DROP TABLE public.read_side_offsets;

CREATE TABLE public.read_side_offsets (
	read_side_id varchar(255) NOT NULL,
	tag varchar(255) NOT NULL,
	sequence_offset int8 NULL,
	time_uuid_offset bpchar(36) NULL,
	CONSTRAINT read_side_offsets_pk PRIMARY KEY (read_side_id, tag)
);

-- Drop table

-- DROP TABLE public."snapshot";

CREATE TABLE public."snapshot" (
	persistence_id varchar(255) NOT NULL,
	sequence_number int8 NOT NULL,
	created int8 NOT NULL,
	"snapshot" bytea NOT NULL,
	CONSTRAINT snapshot_pk PRIMARY KEY (persistence_id, sequence_number)
);