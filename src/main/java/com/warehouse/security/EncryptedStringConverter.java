package com.warehouse.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Transparently encrypts a column at rest with {@link EncryptionService}.
 *
 * <p>Apply with {@code @Convert(converter = EncryptedStringConverter.class)} on the
 * entity field. Reads tolerate legacy plaintext, so existing rows keep working and
 * are upgraded to ciphertext the next time they are saved — no backfill job and no
 * downtime.</p>
 *
 * <p>Hibernate instantiates converters itself rather than pulling them from the
 * Spring context, hence the static handle wired by {@link Bootstrap}.</p>
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static volatile EncryptionService encryptionService;

    @Component
    static class Bootstrap implements ApplicationContextAware {
        @Override
        public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
            EncryptedStringConverter.encryptionService = applicationContext.getBean(EncryptionService.class);
        }
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        EncryptionService service = encryptionService;
        if (service == null || attribute == null) return attribute;
        return service.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        EncryptionService service = encryptionService;
        if (service == null || dbData == null) return dbData;
        return service.decrypt(dbData);
    }
}
