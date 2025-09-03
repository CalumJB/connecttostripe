ALTER TABLE mailchimp_users 
ADD COLUMN audience_status VARCHAR(255) CHECK (audience_status IN ('pending', 'transactional', 'subscribed'));