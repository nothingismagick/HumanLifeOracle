package net.corda.examples.oracle.base.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.utilities.unwrap

// Simple flow that requests the ALIVE response from the specified oracle.
@InitiatingFlow
class QueryHumanLife(val oracle: Party, val ssn: String) : FlowLogic<Boolean>() {
    @Suspendable override fun call() = initiateFlow(oracle).sendAndReceive<Boolean>(ssn).unwrap { it }
}