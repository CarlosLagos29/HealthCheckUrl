package dev.healthcheck.urlcheck.batch

import dev.healthcheck.config.HealthCheckProperties
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [MaxBatchSizeValidator::class])
annotation class MaxBatchSize(
    val message: String = "exceeds the batch limit",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class MaxBatchSizeValidator(private val properties: HealthCheckProperties) :
    ConstraintValidator<MaxBatchSize, List<*>> {

    override fun isValid(value: List<*>?, context: ConstraintValidatorContext): Boolean {
        val max = properties.batch.maxUrls
        if (value == null || value.size <= max) return true

        context.disableDefaultConstraintViolation()
        context.buildConstraintViolationWithTemplate("must contain at most $max URLs").addConstraintViolation()
        return false
    }
}
