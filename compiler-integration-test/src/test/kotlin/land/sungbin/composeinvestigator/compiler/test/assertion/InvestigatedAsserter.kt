// Copyright 2024 Ji Sungbin
// SPDX-License-Identifier: Apache-2.0
package land.sungbin.composeinvestigator.compiler.test.assertion

import assertk.Assert
import assertk.assertions.isDataClassEqualTo
import assertk.fail
import land.sungbin.composeinvestigator.compiler.test.Investigated
import land.sungbin.composeinvestigator.runtime.InvalidationResult

fun Assert<List<Investigated>>.assertInvestigations(vararg investigates: Investigated) {
  given { actuals ->
    if (actuals.size != investigates.size)
      fail(
        "Expected ${investigates.size} investigations, but found ${actuals.size}",
        expected = investigates,
        actual = actuals,
      )

    repeat(actuals.size) { index ->
      val (actualInformation, actualResult) = actuals[index]
      val (investigateInformation, investigateResult) = investigates[index]

      assertThat(actualInformation.copy(compoundKey = null))
        .isDataClassEqualTo(investigateInformation.copy(compoundKey = null))

      if (
        actualResult is InvalidationResult.ArgumentChanged &&
        investigateResult is InvalidationResult.ArgumentChanged
      ) {
        val maskedActualResult = InvalidationResult.ArgumentChanged(
          changed = actualResult.changed.map { changedArgument ->
            if (!changedArgument.previous.type.contains("Function")) return@map changedArgument
            changedArgument.copy(
              previous = changedArgument.previous.copy(valueHashCode = 0),
              new = changedArgument.new.copy(valueHashCode = 0),
            )
          },
        )
        val maskedInvestigateResult = InvalidationResult.ArgumentChanged(
          changed = investigateResult.changed.map { changedArgument ->
            if (!changedArgument.previous.type.contains("Function")) return@map changedArgument
            changedArgument.copy(
              previous = changedArgument.previous.copy(valueHashCode = 0),
              new = changedArgument.new.copy(valueHashCode = 0),
            )
          },
        )

        assertThat(maskedActualResult).isDataClassEqualTo(maskedInvestigateResult)
      } else {
        assertThat(actualResult).isDataClassEqualTo(investigateResult)
      }
    }
  }
}
