-- Kargonomi, depo tanımlanmamış her gönderide gönderici için vergi/kimlik numarası istiyor ve
-- eksikse isteğin tamamını reddediyor. Bu alan hiç gönderilmiyordu.
--
-- Boş bırakılabilir: kod boşsa faturalar için zaten girilmiş olan invoice_company_tax_id'ye
-- düşüyor. Buraya değer yazmak, kargo göndericisi fatura kesen tüzel kişiden farklı olduğunda
-- gerekir.
INSERT INTO site_settings (setting_key, setting_value, setting_type) VALUES
    ('sender_tax_number', '', 'STRING')
ON CONFLICT (setting_key) DO NOTHING;
