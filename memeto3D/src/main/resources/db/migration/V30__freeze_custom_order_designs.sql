CREATE TABLE order_custom_designs (
    id uuid PRIMARY KEY,
    order_id uuid NOT NULL UNIQUE REFERENCES print_orders(id),
    order_item_id uuid NOT NULL UNIQUE REFERENCES order_items(id),
    model_output_id uuid NOT NULL REFERENCES model_outputs(id),
    snapshot_json jsonb NOT NULL,
    snapshot_sha256 varchar(64) NOT NULL,
    requested_height_cm integer NOT NULL CHECK(requested_height_cm BETWEEN 8 AND 20),
    status varchar(30) NOT NULL DEFAULT 'QUEUED' CHECK(status IN ('QUEUED','EXPORTING','READY','APPROVED','REJECTED','FAILED')),
    lease_token uuid,
    provider varchar(30), object_key text, location text, artifact_sha256 varchar(64), artifact_size_bytes bigint,
    model_version_id uuid REFERENCES model_versions(id),
    error_code varchar(100), review_notes text, reviewed_by uuid REFERENCES users(id), reviewed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_custom_design_export ON order_custom_designs(status,updated_at);
-- Snapshot content may never be silently changed by later draft edits or retries.
CREATE FUNCTION protect_custom_order_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.snapshot_json IS DISTINCT FROM OLD.snapshot_json OR NEW.snapshot_sha256 IS DISTINCT FROM OLD.snapshot_sha256
    OR NEW.model_output_id IS DISTINCT FROM OLD.model_output_id OR NEW.order_id IS DISTINCT FROM OLD.order_id
    OR NEW.order_item_id IS DISTINCT FROM OLD.order_item_id OR NEW.requested_height_cm IS DISTINCT FROM OLD.requested_height_cm THEN
   RAISE EXCEPTION 'Custom order snapshot is immutable';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER guard_custom_order_snapshot BEFORE UPDATE ON order_custom_designs
FOR EACH ROW EXECUTE FUNCTION protect_custom_order_snapshot();
-- Lock the user before custom checkout to serialize idempotency decisions.
CREATE UNIQUE INDEX uq_custom_checkout_key ON print_orders(user_id,idempotency_key)
WHERE idempotency_key LIKE 'custom:%';
