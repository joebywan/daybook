-- One row per install per day it sent: a second send that day replaces the first, earlier days are kept.
-- WITHOUT ROWID makes the primary key the table itself, so a send costs one row written, not two (D1 counts index rows).
CREATE TABLE reports (
  install  TEXT NOT NULL,  -- random UUID made on the device; not derived from anything
  sent_on  TEXT NOT NULL,  -- the report's own date, YYYY-MM-DD
  received TEXT NOT NULL,  -- the day it arrived, YYYY-MM-DD (UTC)
  payload  TEXT NOT NULL,  -- the report as validated: known fields only
  PRIMARY KEY (install, sent_on)
) WITHOUT ROWID;
