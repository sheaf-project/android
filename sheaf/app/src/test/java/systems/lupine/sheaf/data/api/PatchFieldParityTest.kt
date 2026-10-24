package systems.lupine.sheaf.data.api

import com.squareup.moshi.Json
import systems.lupine.sheaf.data.model.GroupRuleSpec
import systems.lupine.sheaf.data.model.GroupUpdate
import systems.lupine.sheaf.data.model.MemberRuleSpec
import systems.lupine.sheaf.data.model.MemberUpdate
import systems.lupine.sheaf.data.model.NotificationChannelUpdate
import systems.lupine.sheaf.data.model.QuietHoursSpec
import systems.lupine.sheaf.data.model.SystemUpdate
import systems.lupine.sheaf.di.NetworkModule
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every field on a hand-written PATCH body reaches the wire.
 *
 * A hand-written adapter only writes what it lists, so a field added to the
 * data class and not to the adapter is dropped in silence. That has already
 * happened twice: member ceilings and the step-up credentials were both added
 * to the body and not to the adapter, so re-auth never reached the server and
 * the guard switches never saved.
 *
 * The per-body tests next door guard that too, but by a hand-written list of
 * names, so they only cover the fields somebody remembered to add to them. A
 * new field passes them by construction. This one asks the data class what its
 * fields are, so adding one and forgetting the adapter fails here with the
 * field's own name in the message.
 *
 * Mirrors the shape of the server's own importer/exporter parity test, and for
 * the same reason: the failure mode is silence.
 */
class PatchFieldParityTest {

    private val moshi = NetworkModule.provideMoshi()

    @Test fun `every member field reaches the wire`() = assertAllFieldsSerialise(MemberUpdate::class)

    @Test fun `every group field reaches the wire`() = assertAllFieldsSerialise(GroupUpdate::class)

    @Test fun `every system field reaches the wire`() = assertAllFieldsSerialise(SystemUpdate::class)

    @Test fun `every channel field reaches the wire`() =
        assertAllFieldsSerialise(NotificationChannelUpdate::class)

    /**
     * Populate every field, serialise, and insist each one shows up.
     *
     * Booleans are set true throughout, which also turns on the flags that let
     * the clearable fields through ([MemberUpdate.clearsUnsetFields] and
     * [NotificationChannelUpdate.quietHoursSet]), so this exercises the widest
     * body each adapter can produce.
     */
    private fun <T : Any> assertAllFieldsSerialise(klass: KClass<T>) {
        val body = klass.primaryConstructor
            ?.let { ctor -> ctor.callBy(ctor.parameters.associateWith { sampleFor(it) }) }
            ?: fail("${klass.simpleName} has no primary constructor")

        @Suppress("UNCHECKED_CAST")
        val json = moshi.adapter(klass.java as Class<Any>).toJson(body)

        val missing = wireNames(klass).filterNot { "\"$it\":" in json }
        assertTrue(
            missing.isEmpty(),
            "${klass.simpleName} does not send ${missing.joinToString()}. " +
                "Add them to its adapter in PatchJsonAdapters.kt, choosing clears() or " +
                "omitsWhenNull() to match the server's _reject_explicit_null list. Got: $json",
        )
    }

    /** The JSON names this body is expected to carry, skipping ignored ones. */
    private fun wireNames(klass: KClass<*>): List<String> =
        klass.primaryConstructor!!.parameters.mapNotNull { param ->
            val annotation = param.jsonAnnotation(klass)
            when {
                annotation?.ignore == true -> null
                annotation != null && annotation.name != Json.UNSET_NAME -> annotation.name
                // No @Json means the Kotlin name is the wire name, which holds
                // for every one of these bodies (name, description, colour and
                // the step-up credentials).
                else -> param.name
            }
        }

    /**
     * The `@Json` for a constructor parameter, from the parameter or from the
     * property it declares. Which of the two carries it depends on how it was
     * written, and both spellings appear in these models.
     */
    private fun KParameter.jsonAnnotation(klass: KClass<*>): Json? =
        findAnnotation<Json>()
            ?: klass.memberProperties.firstOrNull { it.name == name }?.findAnnotation()

    private fun sampleFor(param: KParameter): Any = when (param.type.classifierName()) {
        "String" -> "x"
        "Boolean" -> true
        "Int" -> 1
        "Long" -> 1L
        "Double" -> 1.0
        "List" -> listForElement(param.type)
        "Map" -> mapOf("k" to "v")
        "QuietHoursSpec" -> QuietHoursSpec("22:00", "07:00", "UTC")
        else -> fail(
            "No sample value for ${param.name}: ${param.type}. Add one to sampleFor so this " +
                "body stays covered.",
        )
    }

    private fun listForElement(type: KType): List<Any> =
        when (type.arguments.firstOrNull()?.type?.classifierName()) {
            "String" -> listOf("x")
            "GroupRuleSpec" -> listOf(GroupRuleSpec(groupId = "g", rule = "allow"))
            "MemberRuleSpec" -> listOf(MemberRuleSpec(memberId = "m", rule = "allow"))
            else -> fail("No sample element for $type. Add one to listForElement.")
        }

    private fun KType.classifierName(): String? = (classifier as? KClass<*>)?.simpleName
}
