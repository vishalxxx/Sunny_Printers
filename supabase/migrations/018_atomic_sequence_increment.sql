-- Atomic sequence increment function
CREATE OR REPLACE FUNCTION public.increment_number_sequence(seq_key text, ref_fy text)
RETURNS TABLE (
    sequence_key text,
    display_name text,
    prefix text,
    current_number bigint,
    digit_width integer,
    financial_year text
) AS $$
BEGIN
    RETURN QUERY
    INSERT INTO public.number_sequences (sequence_key, display_name, prefix, current_number, digit_width, financial_year, updated_at)
    VALUES (seq_key, seq_key, '', 1, 4, ref_fy, now())
    ON CONFLICT (sequence_key) DO UPDATE
    SET current_number = CASE 
        WHEN public.number_sequences.financial_year <> ref_fy THEN 0 
        ELSE public.number_sequences.current_number 
    END + 1,
        financial_year = ref_fy,
        updated_at = now()
    RETURNING public.number_sequences.sequence_key, public.number_sequences.display_name, public.number_sequences.prefix, public.number_sequences.current_number, public.number_sequences.digit_width, public.number_sequences.financial_year;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
