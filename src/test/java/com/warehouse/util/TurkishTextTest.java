package com.warehouse.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The duplicate-delivery warning is only as good as this matcher: too loose and every common
 * name trips it, too strict and the suffixed names in Turkish delivery notes never match.
 */
class TurkishTextTest {

    // ─── normalisation ───────────────────────────────────────────────────────

    @Test
    void should_fold_turkish_letters_and_casing() {
        assertThat(TurkishText.normalize("Ayşe YILMAZ")).isEqualTo("ayse yilmaz");
        assertThat(TurkishText.normalize("İsmail Çağrı Öztürk")).isEqualTo("ismail cagri ozturk");
        assertThat(TurkishText.normalize("ŞÜKRÜ  GÜNEŞ")).isEqualTo("sukru gunes");
    }

    @Test
    void should_split_on_punctuation_so_suffixes_detach_from_the_name() {
        // The apostrophe becomes a break, which leaves the bare name as its own token.
        assertThat(TurkishText.normalize("  Ayşe   Yılmaz'ın,  ")).isEqualTo("ayse yilmaz in");
    }

    @Test
    void should_drop_short_tokens_and_stop_words() {
        assertThat(TurkishText.tokens("Sayın Ayşe Yılmaz'a teslim edildi"))
            .containsExactly("ayse", "yilmaz");
    }

    @Test
    void should_still_match_a_suffix_written_without_an_apostrophe() {
        assertThat(TurkishText.nameOccursIn("Ayşe Yılmaz", "Ayse Yilmaza teslim edildi")).isTrue();
    }

    // ─── name matching ───────────────────────────────────────────────────────

    @Test
    void should_match_the_same_name_written_without_turkish_letters() {
        assertThat(TurkishText.nameOccursIn("Ayşe Yılmaz", "AYSE YILMAZ")).isTrue();
    }

    @Test
    void should_match_a_name_that_carries_a_suffix_inside_a_note() {
        assertThat(TurkishText.nameOccursIn("Ayşe Yılmaz", "Ayşe Yılmaz'a 2 adet teslim edildi")).isTrue();
        assertThat(TurkishText.nameOccursIn("Mehmet Demir", "Mehmet Demir'in siparişi elden verildi")).isTrue();
    }

    @Test
    void should_match_regardless_of_word_order() {
        assertThat(TurkishText.nameOccursIn("Yılmaz Ayşe", "Ayşe Yılmaz")).isTrue();
    }

    @Test
    void should_not_match_a_different_person_sharing_one_name() {
        assertThat(TurkishText.nameOccursIn("Ayşe Yılmaz", "Ayşe Kaya")).isFalse();
        assertThat(TurkishText.nameOccursIn("Ayşe Yılmaz", "Fatma Yılmaz")).isFalse();
    }

    @Test
    void a_single_repeated_token_should_not_satisfy_both_name_parts() {
        // "yilmaz yilmaz" must not prove a match for "Ayşe Yılmaz"
        assertThat(TurkishText.nameOccursIn("Ayşe Yılmaz", "Yılmaz Yılmaz")).isFalse();
    }

    @Test
    void single_word_names_should_need_a_whole_token() {
        assertThat(TurkishText.nameOccursIn("Ali", "Ali")).isTrue();
        // prefix-only hits on a one-word name would flag every Alican as Ali
        assertThat(TurkishText.nameOccursIn("Alican", "Ali")).isFalse();
    }

    @Test
    void should_report_which_tokens_matched() {
        assertThat(TurkishText.matchedTokens("Ayşe Yılmaz", "Ayşe Yılmaz'a teslim"))
            .containsExactly("ayse", "yilmaz");
    }

    @Test
    void blank_input_should_never_match() {
        assertThat(TurkishText.nameOccursIn("", "Ayşe Yılmaz")).isFalse();
        assertThat(TurkishText.nameOccursIn("Ayşe Yılmaz", null)).isFalse();
        assertThat(TurkishText.isSearchable("  ")).isFalse();
        assertThat(TurkishText.isSearchable("A.")).isFalse();
    }

    // ─── title casing ────────────────────────────────────────────────────────

    @Test
    void should_title_case_a_name_using_turkish_capitals() {
        assertThat(TurkishText.toTitleCase("ayşe yılmaz")).isEqualTo("Ayşe Yılmaz");
        assertThat(TurkishText.toTitleCase("AYŞE YILMAZ")).isEqualTo("Ayşe Yılmaz");
        // capital of "i" is "İ", capital of "ı" is "I"
        assertThat(TurkishText.toTitleCase("irem ışık")).isEqualTo("İrem Işık");
    }

    @Test
    void should_collapse_whitespace_and_keep_compound_parts_capitalised() {
        assertThat(TurkishText.toTitleCase("  ayşe    yılmaz ")).isEqualTo("Ayşe Yılmaz");
        assertThat(TurkishText.toTitleCase("mehmet-ali kaya")).isEqualTo("Mehmet-Ali Kaya");
    }

    @Test
    void should_keep_turkish_conjunctions_lower_case_inside_a_name() {
        // Firm names carry these constantly and print on signed paperwork, where "Ve" reads
        // as a typo. Only mid-value: a value that opens with the word is not using it as one.
        assertThat(TurkishText.toTitleCase("yıldız kargo ve nakliyat ltd."))
                .isEqualTo("Yıldız Kargo ve Nakliyat Ltd.");
        assertThat(TurkishText.toTitleCase("ATS DTM TARIM VE TİCARET"))
                .isEqualTo("Ats Dtm Tarım ve Ticaret");
        assertThat(TurkishText.toTitleCase("ahmet ile mehmet")).isEqualTo("Ahmet ile Mehmet");
        assertThat(TurkishText.toTitleCase("veysel veli")).isEqualTo("Veysel Veli");
        assertThat(TurkishText.toTitleCase("ve tic. ltd.")).isEqualTo("Ve Tic. Ltd.");
    }

    @Test
    void title_case_should_survive_blank_and_null_input() {
        assertThat(TurkishText.toTitleCase("   ")).isEmpty();
        assertThat(TurkishText.toTitleCase(null)).isNull();
    }

    // ─── product name casing ─────────────────────────────────────────────────

    @Test
    void should_title_case_lower_case_product_words() {
        assertThat(TurkishText.toProductNameCase("profilo buzdolabı")).isEqualTo("Profilo Buzdolabı");
        assertThat(TurkishText.toProductNameCase("ankastre fırın seti")).isEqualTo("Ankastre Fırın Seti");
    }

    @Test
    void should_leave_model_codes_and_capacities_untouched() {
        assertThat(TurkishText.toProductNameCase("profilo BD3086W3VN buzdolabı"))
            .isEqualTo("Profilo BD3086W3VN Buzdolabı");
        assertThat(TurkishText.toProductNameCase("çamaşır makinesi 9KG A+++"))
            .isEqualTo("Çamaşır Makinesi 9KG A+++");
    }

    @Test
    void should_leave_brands_acronyms_and_mixed_case_alone() {
        assertThat(TurkishText.toProductNameCase("LG oled TV")).isEqualTo("LG Oled TV");
        assertThat(TurkishText.toProductNameCase("apple iPhone kılıf")).isEqualTo("Apple iPhone Kılıf");
        // ALL-CAPS "I" is ambiguous in Turkish, so all-caps words are never rewritten.
        assertThat(TurkishText.toProductNameCase("PROFILO BUZDOLABI")).isEqualTo("PROFILO BUZDOLABI");
    }

    // ─── note casing ─────────────────────────────────────────────────────────

    @Test
    void note_that_is_only_a_name_should_be_title_cased() {
        assertThat(TurkishText.toNoteCase("ahmet yılmaz")).isEqualTo("Ahmet Yılmaz");
        assertThat(TurkishText.toNoteCase("AYŞE YILMAZ")).isEqualTo("Ayşe Yılmaz");
    }

    @Test
    void note_that_is_a_sentence_should_keep_its_wording() {
        // Title casing would read "Ayşe Yılmaz'a Kalan 2 Adet Teslim Edildi".
        assertThat(TurkishText.toNoteCase("ayşe yılmaz'a kalan 2 adet teslim edildi"))
            .isEqualTo("Ayşe yılmaz'a kalan 2 adet teslim edildi");
        assertThat(TurkishText.toNoteCase("iade alındı, depoya kondu"))
            .isEqualTo("İade alındı, depoya kondu");
    }

    @Test
    void note_casing_should_survive_blank_and_null_input() {
        assertThat(TurkishText.toNoteCase("  ")).isEmpty();
        assertThat(TurkishText.toNoteCase(null)).isNull();
    }

    // ─── search columns ──────────────────────────────────────────────────────

    @Test
    void search_column_should_make_turkish_spellings_equal() {
        // The whole point: a record typed "Fehmi Balli" must be found by searching "Ballı".
        String stored = TurkishText.normalizeForSearch("Fehmi Balli", null);
        assertThat(stored).contains(TurkishText.normalize("Ballı"));
        assertThat(stored).contains(TurkishText.normalize("BALLI"));
        assertThat(TurkishText.normalizeForSearch("Fehmi Ballı", null)).isEqualTo(stored);
    }

    @Test
    void search_column_should_carry_the_compact_phone_form() {
        String stored = TurkishText.normalizeForSearch("Ayşe Yılmaz", "0532 111 22 33");
        // Normalising splits the spaced phone, so the digits-only spelling is stored too.
        assertThat(stored).contains("5321112233");
        assertThat(stored).contains("ayse yilmaz");
    }

    @Test
    void search_column_should_be_null_when_there_is_nothing_to_index() {
        assertThat(TurkishText.normalizeForSearch(null, "  ")).isNull();
        assertThat(TurkishText.normalizeForSearch()).isNull();
    }

    @Test
    void search_pattern_should_wrap_the_normalised_query() {
        assertThat(TurkishText.searchPattern("Ballı")).isEqualTo("%balli%");
        assertThat(TurkishText.searchPattern("  ")).isNull();
    }

    // ─── phone matching ──────────────────────────────────────────────────────

    @Test
    void should_compare_phones_by_their_last_ten_digits() {
        assertThat(TurkishText.phonesMatch("0532 111 22 33", "+90 532 111 22 33")).isTrue();
        assertThat(TurkishText.phonesMatch("5321112233", "0532-111-22-33")).isTrue();
        assertThat(TurkishText.phonesMatch("0532 111 22 33", "0532 111 22 34")).isFalse();
    }

    @Test
    void incomplete_phones_should_not_match() {
        assertThat(TurkishText.phonesMatch("111", "111")).isFalse();
        assertThat(TurkishText.phonesMatch(null, "5321112233")).isFalse();
    }
}
