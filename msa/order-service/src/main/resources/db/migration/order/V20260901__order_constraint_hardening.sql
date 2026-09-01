ALTER TABLE invoice_application
  ADD UNIQUE KEY uk_invoice_application_order_id (order_id);
