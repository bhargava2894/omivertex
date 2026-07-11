-- Exit lifecycle: who left, when, and why. lastWorkingDay drives auto-cleanup
-- (status flip + allocation end) and the exits KPI.
ALTER TABLE associates ADD COLUMN resignation_date date;
ALTER TABLE associates ADD COLUMN last_working_day date;
ALTER TABLE associates ADD COLUMN exit_reason varchar(255);
ALTER TABLE associates ADD CONSTRAINT associates_exit_reason_check
    CHECK (exit_reason IN ('RESIGNED','TERMINATED','CONTRACT_ENDED','RETIRED','OTHER'));
