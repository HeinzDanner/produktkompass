package de.heinzdanner.produktkompass.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class HttpUrlValidator implements ConstraintValidator<HttpUrl, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.isBlank() || HttpUrls.normalize(value) != null;
    }
}
