CREATE TABLE IF NOT EXISTS public.metrics
(
"type" VARCHAR(256) ENCODE lzo
,workload VARCHAR(256) ENCODE lzo
,context VARCHAR(256) ENCODE lzo
,tenant_id VARCHAR(256) ENCODE lzo
,tenant_name VARCHAR(256) ENCODE lzo
,tenant_tier VARCHAR(256) ENCODE lzo
,timerecorded TIMESTAMP WITH TIME ZONE ENCODE az64
,metric_name VARCHAR(256) ENCODE lzo
,metric_unit VARCHAR(256) ENCODE lzo
,metric_value NUMERIC(18,0) ENCODE az64
,meta_data VARCHAR(256) ENCODE lzo
)
DISTSTYLE AUTO
;
