package net.corda.examples.oracle.client.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.examples.oracle.base.contract.HUMAN_LIFE_PROGRAM_ID
import net.corda.examples.oracle.base.contract.HumanLifeContract
import net.corda.examples.oracle.base.contract.HumanLifeState
import net.corda.examples.oracle.base.flow.QueryHumanLife
import net.corda.examples.oracle.base.flow.SignHumanLife
import java.util.function.Predicate

// The client-side flow that:
// - Uses 'QueryHumanLife' to request the ALIVE response for a given SSN
// - Adds it to a transaction and signs it
// - Uses 'SignHumanLife' to gather the oracle's signature attesting that this really is the ALIVE response for the given SSN
// - Finalises the transaction
@InitiatingFlow
@StartableByRPC
class CreateHumanLife(val ssn : String) : FlowLogic<SignedTransaction>() {

    companion object {
        object SET_UP : ProgressTracker.Step("Initialising flow.")
        object QUERYING_THE_ORACLE : ProgressTracker.Step("Querying oracle for the ALIVE response.")
        object BUILDING_THE_TX : ProgressTracker.Step("Building transaction.")
        object VERIFYING_THE_TX : ProgressTracker.Step("Verifying transaction.")
        object WE_SIGN : ProgressTracker.Step("signing transaction.")
        object ORACLE_SIGNS : ProgressTracker.Step("Requesting oracle signature.")
        object FINALISING : ProgressTracker.Step("Finalising transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(SET_UP, QUERYING_THE_ORACLE, BUILDING_THE_TX,
                VERIFYING_THE_TX, WE_SIGN, ORACLE_SIGNS, FINALISING)
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = SET_UP
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        // In Corda v1.0, we identify oracles we want to use by name.
        val oracleName = CordaX500Name("Oracle", "New York","US")
        val oracle = serviceHub.networkMapCache.getNodeByLegalName(oracleName)?.legalIdentities?.first()
                 ?: throw IllegalArgumentException("Requested oracle $oracleName not found on network.")

        progressTracker.currentStep = QUERYING_THE_ORACLE
        val humanLifeRequestedFromOracle = subFlow(QueryHumanLife(oracle, ssn))

        progressTracker.currentStep = BUILDING_THE_TX
        val humanLifeState = HumanLifeState(ssn, humanLifeRequestedFromOracle, ourIdentity)
        val humanLifeCmdData = HumanLifeContract.Create(ssn, humanLifeRequestedFromOracle)
        // By listing the oracle here, we make the oracle a required signer.
        val humanLifeCmdRequiredSigners = listOf(oracle.owningKey, ourIdentity.owningKey)
        val builder = TransactionBuilder(notary)
                .addOutputState(humanLifeState, HUMAN_LIFE_PROGRAM_ID)
                .addCommand(humanLifeCmdData, humanLifeCmdRequiredSigners)

        progressTracker.currentStep = VERIFYING_THE_TX
        builder.verify(serviceHub)

        progressTracker.currentStep = WE_SIGN
        val ptx = serviceHub.signInitialTransaction(builder)

        progressTracker.currentStep = ORACLE_SIGNS
        // For privacy reasons, we only want to expose to the oracle any commands of type `HumanLifeContract.Create`
        // that require its signature.
        val ftx = ptx.buildFilteredTransaction(Predicate {
            when (it) {
                is Command<*> -> oracle.owningKey in it.signers && it.value is HumanLifeContract.Create
                else -> false
            }
        })

        val oracleSignature = subFlow(SignHumanLife(oracle, ftx))
        val stx = ptx.withAdditionalSignature(oracleSignature)

        progressTracker.currentStep = FINALISING
        return subFlow(FinalityFlow(stx))
    }
}