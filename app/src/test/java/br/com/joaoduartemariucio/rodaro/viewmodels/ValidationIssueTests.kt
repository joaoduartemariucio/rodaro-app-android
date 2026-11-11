package br.com.joaoduartemariucio.rodaro.viewmodels

import br.com.joaoduartemariucio.rodaro.calculations.assertDecimal
import br.com.joaoduartemariucio.rodaro.calculations.day
import br.com.joaoduartemariucio.rodaro.calculations.decimal
import br.com.joaoduartemariucio.rodaro.calculations.full
import br.com.joaoduartemariucio.rodaro.calculations.partial
import br.com.joaoduartemariucio.rodaro.calculations.rule
import br.com.joaoduartemariucio.rodaro.calculations.sample
import br.com.joaoduartemariucio.rodaro.calculations.service
import br.com.joaoduartemariucio.rodaro.calculations.testVehicleId
import br.com.joaoduartemariucio.rodaro.calculations.utc
import br.com.joaoduartemariucio.rodaro.domain.common.errors.AccountError
import br.com.joaoduartemariucio.rodaro.domain.common.errors.RepositoryError
import br.com.joaoduartemariucio.rodaro.fakes.TestLocalizer
import br.com.joaoduartemariucio.rodaro.presentation.common.ValidationIssue
import br.com.joaoduartemariucio.rodaro.presentation.common.blocksSaving
import br.com.joaoduartemariucio.rodaro.presentation.common.message
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidationIssueTests {
    private val readings = listOf(sample(0, 1_000.0), sample(20, 2_000.0))

    @Test
    fun `a missing odometer is required`() {
        assertEquals(listOf(ValidationIssue.OdometerRequired), ValidationIssue.odometer(null, day(10), readings))
    }

    @Test
    fun `an odometer between its neighbors has no issue`() {
        assertTrue(ValidationIssue.odometer(1_500.0, day(10), readings).isEmpty())
    }

    @Test
    fun `an odometer not above the previous reading warns`() {
        val issues = ValidationIssue.odometer(1_000.0, day(10), readings)

        assertEquals(listOf(ValidationIssue.OdometerNotAfterPrevious(1_000.0)), issues)
        assertFalse(issues.blocksSaving)
    }

    @Test
    fun `an odometer not below the next reading warns`() {
        val issues = ValidationIssue.odometer(2_000.0, day(10), readings)

        assertEquals(listOf(ValidationIssue.OdometerNotBeforeNext(2_000.0)), issues)
        assertFalse(issues.blocksSaving)
    }

    @Test
    fun `only the odometer order is a warning - everything else blocks saving`() {
        val warnings = listOf(ValidationIssue.OdometerNotAfterPrevious(1.0), ValidationIssue.OdometerNotBeforeNext(1.0))
        val errors =
            listOf(
                ValidationIssue.NameRequired,
                ValidationIssue.OdometerRequired,
                ValidationIssue.LitersRequired,
                ValidationIssue.LitersNotPositive,
                ValidationIssue.PriceRequired,
                ValidationIssue.PriceNotPositive,
                ValidationIssue.CostNegative,
                ValidationIssue.RuleWithoutInterval,
            )

        assertFalse(warnings.any { it.isBlocking })
        assertTrue(errors.all { it.isBlocking })
        assertTrue((warnings + ValidationIssue.NameRequired).blocksSaving)
    }

    @Test
    fun `every issue has its own message`() {
        val issues =
            listOf(
                ValidationIssue.NameRequired,
                ValidationIssue.OdometerRequired,
                ValidationIssue.OdometerNotAfterPrevious(1_000.0),
                ValidationIssue.OdometerNotBeforeNext(2_000.0),
                ValidationIssue.LitersRequired,
                ValidationIssue.LitersNotPositive,
                ValidationIssue.PriceRequired,
                ValidationIssue.PriceNotPositive,
                ValidationIssue.CostNegative,
                ValidationIssue.RuleWithoutInterval,
            )

        assertEquals(issues.size, issues.map { it.message(TestLocalizer()) }.toSet().size)
    }

    @Test
    fun `repository errors read as user-facing messages`() {
        val localizer = TestLocalizer()

        assertEquals("This vehicle no longer exists.", RepositoryError.VehicleNotFound(UUID.randomUUID()).message(localizer))
        assertEquals("This record no longer exists.", RepositoryError.EntryNotFound(UUID.randomUUID()).message(localizer))
        assertEquals("Could not save your changes.", RepositoryError.PersistenceFailed(IllegalStateException()).message(localizer))
        assertEquals("boom", IllegalStateException("boom").message(localizer))
        assertEquals("Could not save your changes.", IllegalStateException().message(localizer))
    }

    @Test
    fun `account errors read as user-facing messages`() {
        val localizer = TestLocalizer()

        assertEquals("Enter a valid e-mail address.", AccountError.InvalidEmail.message(localizer))
        assertEquals("E-mail or password is wrong.", AccountError.InvalidCredentials.message(localizer))
        assertEquals("This e-mail is already registered. Sign in instead.", AccountError.EmailInUse.message(localizer))
        assertEquals("Use a password with at least 6 characters.", AccountError.WeakPassword.message(localizer))
        assertEquals("Too many attempts. Wait a moment and try again.", AccountError.TooManyRequests.message(localizer))
        assertEquals("No connection. Check your internet and try again.", AccountError.Network.message(localizer))
        assertEquals("Confirm your identity again to continue.", AccountError.RecentLoginRequired.message(localizer))
        assertEquals("This sign-in method is not enabled for this app.", AccountError.OperationNotAllowed.message(localizer))
        assertEquals("Google sign-in is not set up for this app yet.", AccountError.GoogleNotConfigured.message(localizer))
        assertEquals("No Google account found on this device.", AccountError.NoGoogleAccount.message(localizer))
        assertEquals("Could not complete the request.", AccountError.Cancelled.message(localizer))
        assertEquals("You are not signed in.", AccountError.NotSignedIn.message(localizer))
        assertEquals("Could not complete the request.", AccountError.Failed(Exception("reason")).message(localizer))
        assertEquals("Could not complete the request.", AccountError.Failed(IllegalStateException()).message(localizer))
    }
}
