-- A-02: приглашение игроков по ссылке и QR-коду; токен можно сбросить, в экспорт не идёт
ALTER TABLE campaign ADD COLUMN invite_token VARCHAR(64);
CREATE UNIQUE INDEX campaign_invite_token ON campaign (invite_token);
