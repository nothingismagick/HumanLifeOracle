package net.corda.examples.oracle.client.api

import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.getOrThrow
import net.corda.examples.oracle.base.contract.HumanLifeState
import net.corda.examples.oracle.client.flow.CreateHumanLife
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("human-lives")
class ClientApi(val rpcOps: CordaRPCOps) {
    private val myLegalName = rpcOps.nodeInfo().legalIdentities.first().name

    /**
     * Returns the node's name.
     */
    @GET
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    fun whoami() = mapOf("me" to myLegalName)

    /**
     * Returns all parties registered with the [NetworkMapService].
     */
    @GET
    @Path("peers")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPeers(): Map<String, List<String>> {
        val peers = rpcOps.networkMapSnapshot()
                .map { it.legalIdentities.first().name.toString() }
        return mapOf("peers" to peers)
    }

    /**
     * Enumerates all the prime numbers we currently have in the vault.
     */
    @GET
    @Path("all-verifications")
    @Produces(MediaType.APPLICATION_JSON)
    fun humanLifeStates() = rpcOps.vaultQueryBy<HumanLifeState>().states

    /**
     * Creates a new prime number by consulting the primes oracle.
     */
    @GET
    @Path("verify")
    @Produces(MediaType.APPLICATION_JSON)
    fun createHumanLifeVerification(@QueryParam(value = "ssn") ssn: String): Response {
        // Start the CreateHumanLife flow. We block and wait for the flow to return.
        val (status, message) = try {
            val flowHandle = rpcOps.startFlowDynamic(CreateHumanLife::class.java, ssn)
            val result = flowHandle.returnValue.getOrThrow().tx.outputsOfType<HumanLifeState>().single()
            // Return the response.
            Response.Status.CREATED to "$result"
        } catch (e: Exception) {
            // For the purposes of this demo app, we do not differentiate by exception type.
            Response.Status.BAD_REQUEST to e.message
        }

        return Response
                .status(status)
                .entity(mapOf("result" to message))
                .build()
    }
}