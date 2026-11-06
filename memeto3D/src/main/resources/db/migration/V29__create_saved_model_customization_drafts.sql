-- One editable JSON draft per immutable base output; no GLB export on save.
CREATE TABLE public.customization_drafts (
    id UUID PRIMARY KEY,
    model_output_id UUID NOT NULL REFERENCES public.model_outputs(id) ON DELETE CASCADE,
    fit_result_id UUID NOT NULL REFERENCES public.customization_fit_results(id) ON DELETE RESTRICT,
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE RESTRICT,
    revision BIGINT NOT NULL CHECK(revision>=1),
    selection_json JSONB NOT NULL CHECK(jsonb_typeof(selection_json)='object'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_customization_draft_output UNIQUE(model_output_id)
);
CREATE INDEX idx_customization_drafts_user ON public.customization_drafts(user_id,updated_at DESC);
