-- Correct the manufacturer's canonical CC560 page without editing an applied migration.
UPDATE products
SET source_url = 'https://www.deepcool.com/products/Cases/CC560-Mid-Tower-ATX-PC-Case/2021/15216.shtml'
WHERE sku = 'DEEPCOOL-CC560';
