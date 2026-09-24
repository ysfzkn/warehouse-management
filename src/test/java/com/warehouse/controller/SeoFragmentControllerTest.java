package com.warehouse.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The SEO fragments through the real security filter chain, the way nginx's SSI subrequest
 * calls them.
 *
 * <p>The public chain ends in denyAll(), so a missing permit would make every storefront
 * page silently fall back to the static title — nginx turns any error into the default.
 * The 404 must also have an empty body: nginx pastes whatever the backend answers into
 * the page's head (it intercepts errors today, but that is one directive away from not).</p>
 *
 * <p>MockMvc on the shared test context on purpose: written first as a RANDOM_PORT test, it
 * shared PenetrationTest's context and in the full suite PenetrationTest's admin login then
 * failed with 401 (both passed alone and together). Likely the early-cached context saw the
 * shared in-memory H2 rebuilt by other contexts in between.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeoFragmentControllerTest {

    @Autowired private MockMvc mvc;

    @Test
    @DisplayName("Ana sayfa head parçası kimliksiz çağrıya HTML döner")
    void theHomepageHeadIsPublicHtml() throws Exception {
        mvc.perform(get("/seo/head/"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("<title>")))
                .andExpect(content().string(containsString("rel=\"canonical\"")));
    }

    @Test
    @DisplayName("Sorgu dizeli ana sayfa da tanınır (nginx ham URI'yi iletir)")
    void aQueryStringDoesNotHideTheHomepage() throws Exception {
        mvc.perform(get("/seo/body/").queryParam("utm_source", "x"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>")));
    }

    @Test
    @DisplayName("Bilinmeyen ürün boş gövdeli 404 alır")
    void anUnknownProductIsAnEmptyNotFound() throws Exception {
        mvc.perform(get("/seo/head/urun/boyle-bir-urun-yok"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("Vitrinde olmayan bölüm ve slot 404 alır")
    void unknownSectionsAndSlotsAreNotFound() throws Exception {
        mvc.perform(get("/seo/head/sepet/x")).andExpect(status().isNotFound());
        mvc.perform(get("/seo/footer/")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Yalnızca GET açıktır")
    void onlyGetIsOpen() throws Exception {
        mvc.perform(post("/seo/head/")).andExpect(status().is4xxClientError());
    }
}
