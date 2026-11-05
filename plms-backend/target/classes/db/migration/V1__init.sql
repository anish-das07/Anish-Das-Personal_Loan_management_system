-- PLMS schema for MySQL 8. Each block is owned by one module; there are deliberately no foreign keys
-- across module boundaries (cross-module references are stable IDs validated through service APIs).
-- Money uses DECIMAL(15,2); timestamps are stored in UTC.

-- ===== auth module =====
CREATE TABLE users (
  id            CHAR(36)     NOT NULL PRIMARY KEY,
  email         VARCHAR(254) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  full_name     VARCHAR(120) NOT NULL,
  status        VARCHAR(20)  NOT NULL,
  created_at    DATETIME(6)  NOT NULL,
  version       BIGINT,
  CONSTRAINT uq_users_email UNIQUE (email),
  INDEX idx_users_status (status)
);

CREATE TABLE user_roles (
  user_id   CHAR(36)    NOT NULL,
  role_code VARCHAR(30) NOT NULL,
  CONSTRAINT uq_user_role UNIQUE (user_id, role_code),
  CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE refresh_tokens (
  id             CHAR(36)    NOT NULL PRIMARY KEY,
  user_id        CHAR(36)    NOT NULL,
  family_id      CHAR(36)    NOT NULL,
  token_hash     VARCHAR(64) NOT NULL,
  expires_at     DATETIME(6) NOT NULL,
  revoked_at     DATETIME(6),
  replaced_by_id CHAR(36),
  created_at     DATETIME(6) NOT NULL,
  CONSTRAINT uq_refresh_hash UNIQUE (token_hash),
  INDEX idx_refresh_user (user_id),
  INDEX idx_refresh_family (family_id)
);

CREATE TABLE password_reset_tokens (
  id         CHAR(36)    NOT NULL PRIMARY KEY,
  user_id    CHAR(36)    NOT NULL,
  token_hash VARCHAR(64) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  used_at    DATETIME(6),
  CONSTRAINT uq_reset_hash UNIQUE (token_hash)
);

-- ===== customer module =====
CREATE TABLE customer_profile (
  id                           CHAR(36)     NOT NULL PRIMARY KEY,
  full_name                    VARCHAR(120) NOT NULL,
  email                        VARCHAR(254),
  date_of_birth                DATE,
  phone                        VARCHAR(20),
  address_line1                VARCHAR(200),
  city                         VARCHAR(80),
  state                        VARCHAR(80),
  postal_code                  VARCHAR(12),
  employment_type              VARCHAR(30),
  employer_name                VARCHAR(120),
  employment_start_date        DATE,
  occupation                   VARCHAR(80),
  monthly_income               DECIMAL(15,2),
  existing_monthly_obligations DECIMAL(15,2),
  updated_at                   DATETIME(6)  NOT NULL,
  version                      BIGINT
);

-- ===== product module =====
CREATE TABLE loan_product (
  id                     CHAR(36)      NOT NULL PRIMARY KEY,
  code                   VARCHAR(40)   NOT NULL,
  name                   VARCHAR(100)  NOT NULL,
  description            VARCHAR(500),
  purpose                VARCHAR(60),
  min_amount             DECIMAL(15,2) NOT NULL,
  max_amount             DECIMAL(15,2) NOT NULL,
  min_months             INT           NOT NULL,
  max_months             INT           NOT NULL,
  annual_rate            DECIMAL(5,2)  NOT NULL,
  interest_method        VARCHAR(30)   NOT NULL,
  processing_fee_percent DECIMAL(5,2)  NOT NULL,
  active_from            DATE          NOT NULL,
  active_to              DATE,
  active                 BOOLEAN       NOT NULL,
  policy_version         VARCHAR(40)   NOT NULL,
  version                BIGINT,
  CONSTRAINT uq_product_code UNIQUE (code),
  CONSTRAINT chk_product_amounts CHECK (min_amount > 0 AND min_amount <= max_amount),
  CONSTRAINT chk_product_months CHECK (min_months > 0 AND min_months <= max_months)
);

-- ===== eligibility module =====
CREATE TABLE eligibility_policy (
  policy_version           VARCHAR(40)   NOT NULL PRIMARY KEY,
  min_monthly_income       DECIMAL(15,2) NOT NULL,
  pass_max_dti             DECIMAL(5,4)  NOT NULL,
  review_max_dti           DECIMAL(5,4)  NOT NULL,
  min_employment_months    INT           NOT NULL,
  allowed_employment_types VARCHAR(120)  NOT NULL,
  min_age                  INT           NOT NULL,
  max_age                  INT           NOT NULL,
  credit_required          BOOLEAN       NOT NULL,
  credit_reject_below      INT           NOT NULL,
  credit_review_below      INT           NOT NULL,
  auto_reject_on_fail      BOOLEAN       NOT NULL,
  created_at               DATETIME(6)   NOT NULL
);

CREATE TABLE eligibility_assessment (
  id                   CHAR(36)      NOT NULL PRIMARY KEY,
  application_id       CHAR(36)      NOT NULL,
  policy_version       VARCHAR(40)   NOT NULL,
  outcome              VARCHAR(30)   NOT NULL,
  estimated_emi        DECIMAL(15,2),
  dti_ratio            DECIMAL(7,4),
  credit_assessment_id CHAR(36),
  actor_id             VARCHAR(36),
  evaluated_at         DATETIME(6)   NOT NULL,
  INDEX idx_elig_app (application_id, evaluated_at)
);

CREATE TABLE eligibility_rule_result (
  id            CHAR(36)     NOT NULL PRIMARY KEY,
  assessment_id CHAR(36)     NOT NULL,
  seq           INT          NOT NULL,
  rule_code     VARCHAR(40)  NOT NULL,
  result        VARCHAR(10)  NOT NULL,
  reason_code   VARCHAR(60)  NOT NULL,
  explanation   VARCHAR(200) NOT NULL,
  INDEX idx_rule_assessment (assessment_id)
);

-- ===== credit module =====
CREATE TABLE credit_assessment (
  id                 CHAR(36)    NOT NULL PRIMARY KEY,
  application_id     CHAR(36)    NOT NULL,
  customer_id        CHAR(36)    NOT NULL,
  provider_code      VARCHAR(40) NOT NULL,
  provider_reference VARCHAR(60),
  score              INT,
  score_band         VARCHAR(20),
  status             VARCHAR(20) NOT NULL,
  failure_reason     VARCHAR(40),
  requested_by       VARCHAR(36),
  assessed_at        DATETIME(6) NOT NULL,
  INDEX idx_credit_app (application_id)
);

-- ===== application module =====
CREATE TABLE loan_application (
  id                           CHAR(36)      NOT NULL PRIMARY KEY,
  customer_id                  CHAR(36)      NOT NULL,
  product_id                   CHAR(36)      NOT NULL,
  product_name                 VARCHAR(100),
  requested_amount             DECIMAL(15,2) NOT NULL,
  requested_months             INT           NOT NULL,
  purpose                      VARCHAR(80)   NOT NULL,
  employment_type              VARCHAR(30),
  employer_name                VARCHAR(120),
  employment_start_date        DATE,
  monthly_income               DECIMAL(15,2),
  existing_monthly_obligations DECIMAL(15,2),
  monthly_expenses             DECIMAL(15,2),
  status                       VARCHAR(40)   NOT NULL,
  policy_version               VARCHAR(40),
  terms_snapshot               LONGTEXT,
  snapshot_annual_rate         DECIMAL(5,2),
  snapshot_fee_percent         DECIMAL(5,2),
  snapshot_min_amount          DECIMAL(15,2),
  snapshot_max_amount          DECIMAL(15,2),
  snapshot_min_months          INT,
  snapshot_max_months          INT,
  eligibility_outcome          VARCHAR(30),
  assigned_officer_id          CHAR(36),
  create_idempotency_key       VARCHAR(100),
  submit_idempotency_key       VARCHAR(100),
  revision                     INT           NOT NULL DEFAULT 0,
  submitted_at                 DATETIME(6),
  created_at                   DATETIME(6)   NOT NULL,
  updated_at                   DATETIME(6)   NOT NULL,
  version                      BIGINT        NOT NULL DEFAULT 0,
  CONSTRAINT chk_amount_positive CHECK (requested_amount > 0),
  CONSTRAINT chk_months_positive CHECK (requested_months > 0),
  INDEX idx_app_customer_status (customer_id, status),
  INDEX idx_app_submitted_at (submitted_at)
);

CREATE TABLE application_status_history (
  id               CHAR(36)     NOT NULL PRIMARY KEY,
  application_id   CHAR(36)     NOT NULL,
  from_status      VARCHAR(40),
  to_status        VARCHAR(40)  NOT NULL,
  actor_id         VARCHAR(36),
  reason_code      VARCHAR(60),
  customer_message VARCHAR(300),
  occurred_at      DATETIME(6)  NOT NULL,
  INDEX idx_history_app (application_id, occurred_at)
);

-- ===== document module =====
CREATE TABLE document_metadata (
  id             CHAR(36)     NOT NULL PRIMARY KEY,
  application_id CHAR(36)     NOT NULL,
  customer_id    CHAR(36)     NOT NULL,
  category       VARCHAR(30)  NOT NULL,
  object_key     VARCHAR(80)  NOT NULL,
  file_name_safe VARCHAR(100) NOT NULL,
  mime_type      VARCHAR(60)  NOT NULL,
  size_bytes     BIGINT       NOT NULL,
  checksum       VARCHAR(64)  NOT NULL,
  doc_version    INT          NOT NULL,
  status         VARCHAR(30)  NOT NULL,
  uploaded_at    DATETIME(6)  NOT NULL,
  lock_version   BIGINT,
  INDEX idx_doc_app_status (application_id, status)
);

CREATE TABLE document_verification (
  id           CHAR(36)     NOT NULL PRIMARY KEY,
  document_id  CHAR(36)     NOT NULL,
  decision     VARCHAR(30)  NOT NULL,
  reason_code  VARCHAR(60),
  comment_text VARCHAR(500),
  verifier_id  CHAR(36)     NOT NULL,
  decided_at   DATETIME(6)  NOT NULL,
  INDEX idx_verif_doc (document_id)
);

-- ===== approval module =====
CREATE TABLE loan_review (
  id             CHAR(36)     NOT NULL PRIMARY KEY,
  application_id CHAR(36)     NOT NULL,
  reviewer_id    CHAR(36)     NOT NULL,
  reviewer_role  VARCHAR(30)  NOT NULL,
  recommendation VARCHAR(30)  NOT NULL,
  reason_code    VARCHAR(60)  NOT NULL,
  comment_text   VARCHAR(500),
  created_at     DATETIME(6)  NOT NULL,
  INDEX idx_review_app (application_id)
);

CREATE TABLE loan_decision (
  id               CHAR(36)      NOT NULL PRIMARY KEY,
  application_id   CHAR(36)      NOT NULL,
  decision         VARCHAR(30)   NOT NULL,
  approved_amount  DECIMAL(15,2),
  approved_months  INT,
  actor_id         CHAR(36)      NOT NULL,
  reason_code      VARCHAR(60)   NOT NULL,
  comment_redacted VARCHAR(500),
  policy_version   VARCHAR(40),
  decided_at       DATETIME(6)   NOT NULL,
  INDEX idx_decision_app (application_id)
);

-- ===== loan account / EMI module =====
CREATE TABLE loan_account (
  id                     CHAR(36)      NOT NULL PRIMARY KEY,
  application_id         CHAR(36)      NOT NULL,
  customer_id            CHAR(36)      NOT NULL,
  product_name           VARCHAR(100),
  principal              DECIMAL(15,2) NOT NULL,
  annual_rate            DECIMAL(5,2)  NOT NULL,
  tenure_months          INT           NOT NULL,
  processing_fee_percent DECIMAL(5,2)  NOT NULL,
  monthly_emi            DECIMAL(15,2),
  outstanding_principal  DECIMAL(15,2) NOT NULL,
  status                 VARCHAR(30)   NOT NULL,
  disbursed_at           DATETIME(6),
  closed_at              DATETIME(6),
  created_at             DATETIME(6)   NOT NULL,
  version                BIGINT,
  CONSTRAINT uq_account_application UNIQUE (application_id),
  INDEX idx_account_customer (customer_id)
);

CREATE TABLE emi_schedule (
  id                CHAR(36)      NOT NULL PRIMARY KEY,
  loan_account_id   CHAR(36)      NOT NULL,
  installment_no    INT           NOT NULL,
  due_date          DATE          NOT NULL,
  opening_balance   DECIMAL(15,2) NOT NULL,
  principal_due     DECIMAL(15,2) NOT NULL,
  interest_due      DECIMAL(15,2) NOT NULL,
  fee_due           DECIMAL(15,2) NOT NULL,
  total_due         DECIMAL(15,2) NOT NULL,
  principal_balance DECIMAL(15,2) NOT NULL,
  status            VARCHAR(24)   NOT NULL,
  paid_at           DATETIME(6),
  version           BIGINT,
  CONSTRAINT uq_emi_installment UNIQUE (loan_account_id, installment_no),
  INDEX idx_emi_due_status (due_date, status)
);

-- ===== disbursement module =====
CREATE TABLE disbursement (
  id              CHAR(36)      NOT NULL PRIMARY KEY,
  loan_account_id CHAR(36)      NOT NULL,
  idempotency_key VARCHAR(100)  NOT NULL,
  gross_amount    DECIMAL(15,2) NOT NULL,
  fee_amount      DECIMAL(15,2) NOT NULL DEFAULT 0,
  net_amount      DECIMAL(15,2) NOT NULL,
  status          VARCHAR(30)   NOT NULL,
  transaction_ref VARCHAR(100),
  failure_reason  VARCHAR(60),
  requested_by    CHAR(36)      NOT NULL,
  created_at      DATETIME(6)   NOT NULL,
  updated_at      DATETIME(6)   NOT NULL,
  version         BIGINT,
  CONSTRAINT uq_disbursement_idempotency UNIQUE (idempotency_key),
  CONSTRAINT uq_disbursement_transaction UNIQUE (transaction_ref),
  CONSTRAINT chk_disb_net_nonnegative CHECK (net_amount >= 0),
  INDEX idx_disb_account (loan_account_id)
);

-- ===== repayment module =====
CREATE TABLE repayment (
  id              CHAR(36)      NOT NULL PRIMARY KEY,
  loan_account_id CHAR(36)      NOT NULL,
  installment_id  CHAR(36)      NOT NULL,
  installment_no  INT           NOT NULL,
  customer_id     CHAR(36)      NOT NULL,
  idempotency_key VARCHAR(100)  NOT NULL,
  amount          DECIMAL(15,2) NOT NULL,
  payment_method  VARCHAR(30)   NOT NULL,
  status          VARCHAR(20)   NOT NULL,
  reference       VARCHAR(60),
  failure_reason  VARCHAR(60),
  created_at      DATETIME(6)   NOT NULL,
  updated_at      DATETIME(6)   NOT NULL,
  version         BIGINT,
  CONSTRAINT uq_repayment_idempotency UNIQUE (idempotency_key),
  CONSTRAINT uq_repayment_reference UNIQUE (reference),
  CONSTRAINT chk_repayment_positive CHECK (amount > 0),
  INDEX idx_repayment_account (loan_account_id)
);

CREATE TABLE repayment_allocation (
  id             CHAR(36)      NOT NULL PRIMARY KEY,
  repayment_id   CHAR(36)      NOT NULL,
  installment_id CHAR(36)      NOT NULL,
  fee            DECIMAL(15,2) NOT NULL,
  interest       DECIMAL(15,2) NOT NULL,
  principal      DECIMAL(15,2) NOT NULL,
  CONSTRAINT uq_allocation_repayment UNIQUE (repayment_id),
  INDEX idx_allocation_installment (installment_id)
);

-- ===== notification module =====
CREATE TABLE notification (
  id              CHAR(36)      NOT NULL PRIMARY KEY,
  user_id         CHAR(36)      NOT NULL,
  channel         VARCHAR(10)   NOT NULL,
  template        VARCHAR(60)   NOT NULL,
  subject         VARCHAR(160)  NOT NULL,
  body            VARCHAR(1000) NOT NULL,
  delivery_status VARCHAR(20)   NOT NULL,
  created_at      DATETIME(6)   NOT NULL,
  read_at         DATETIME(6),
  INDEX idx_notification_user (user_id)
);

-- ===== audit module (append-only: grant INSERT/SELECT only to the application user) =====
CREATE TABLE audit_event (
  id                CHAR(36)      NOT NULL PRIMARY KEY,
  actor_id          VARCHAR(36),
  action            VARCHAR(80)   NOT NULL,
  entity_type       VARCHAR(60)   NOT NULL,
  entity_id         VARCHAR(100),
  correlation_id    VARCHAR(80),
  occurred_at       DATETIME(6)   NOT NULL,
  outcome           VARCHAR(20)   NOT NULL,
  metadata_redacted VARCHAR(2000),
  INDEX idx_audit_entity (entity_id),
  INDEX idx_audit_actor (actor_id)
);
