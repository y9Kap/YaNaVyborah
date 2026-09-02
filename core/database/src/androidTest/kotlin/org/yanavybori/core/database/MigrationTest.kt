package org.yanavybori.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContentDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        databaseClass = ContentDatabase::class.java,
    )

    @Before
    fun clean() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(DB_NAME)
    }

    @Test
    fun current_schema_opens_and_validates() {
        helper.createDatabase(DB_NAME, 1).close()
        helper.runMigrationsAndValidate(DB_NAME, 1, true).close()
    }

    private companion object { const val DB_NAME = "migration-content-test.db" }
}

@RunWith(AndroidJUnit4::class)
class UserDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        databaseClass = UserDatabase::class.java,
    )

    @Before
    fun clean() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(DB_NAME)
    }

    @Test
    fun current_schema_opens_and_validates() {
        helper.createDatabase(DB_NAME, 3).close()
        helper.runMigrationsAndValidate(DB_NAME, 3, true).close()
    }

    @Test
    fun migration_1_2_preserves_sessions_and_adds_protected_metadata() {
        helper.createDatabase(DB_NAME, 1).apply {
            execSQL(
                "INSERT INTO observation_sessions " +
                    "(id, electionPackId, precinctNumber, startedAt, currentVotingDay, currentStage) " +
                    "VALUES ('session', 'pack', '42', 1, 'day', 'stage')",
            )
            close()
        }

        helper.runMigrationsAndValidate(DB_NAME, 2, true, UserDatabase.MIGRATION_1_2).use { database ->
            database.query(
                "SELECT observerFullName, region, commissionMemberNamesJson, " +
                    "deletionPasswordHash, deletionPasswordSalt " +
                    "FROM observation_sessions WHERE id = 'session'",
            ).use { cursor ->
                cursor.moveToFirst()
                org.junit.Assert.assertEquals("", cursor.getString(0))
                org.junit.Assert.assertEquals("", cursor.getString(1))
                org.junit.Assert.assertEquals("[]", cursor.getString(2))
                org.junit.Assert.assertTrue(cursor.isNull(3))
                org.junit.Assert.assertTrue(cursor.isNull(4))
            }
        }
    }

    @Test
    fun migration_2_3_preserves_reconciliations_and_adds_optional_photo() {
        helper.createDatabase(DB_NAME, 2).apply {
            execSQL(
                "INSERT INTO reconciliation_sessions " +
                    "(id, observationSessionId, votingDayId, definitionId, createdAt, updatedAt, " +
                    "valuesJson, resultsJson) VALUES " +
                    "('reconciliation', 'session', 'day', 'definition', 1, 2, '{}', '[]')",
            )
            close()
        }

        helper.runMigrationsAndValidate(DB_NAME, 3, true, UserDatabase.MIGRATION_2_3).use { database ->
            database.query(
                "SELECT valuesJson, resultsJson, photoMediaId FROM reconciliation_sessions " +
                    "WHERE id = 'reconciliation'",
            ).use { cursor ->
                cursor.moveToFirst()
                org.junit.Assert.assertEquals("{}", cursor.getString(0))
                org.junit.Assert.assertEquals("[]", cursor.getString(1))
                org.junit.Assert.assertTrue(cursor.isNull(2))
            }
        }
    }

    private companion object { const val DB_NAME = "migration-user-test.db" }
}
