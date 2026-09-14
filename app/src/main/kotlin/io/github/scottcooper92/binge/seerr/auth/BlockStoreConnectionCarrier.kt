package io.github.scottcooper92.binge.seerr.auth

import com.google.android.gms.auth.blockstore.BlockstoreClient
import com.google.android.gms.auth.blockstore.DeleteBytesRequest
import com.google.android.gms.auth.blockstore.RetrieveBytesRequest
import com.google.android.gms.auth.blockstore.StoreBytesData
import io.github.scottcooper92.binge.seerr.seerr.SeerrCredentials
import io.github.scottcooper92.binge.seerr.seerr.attempt
import kotlinx.coroutines.tasks.await

/** This app's one Block Store entry. Namespaced because the store is shared across an app's data. */
private const val CONNECTION_KEY = "io.github.scottcooper92.binge.seerr.CONNECTION"

/**
 * The connection carried in Play Services' Block Store, which survives a device-to-device transfer
 * and, where the account supports end-to-end encryption, a cloud restore.
 *
 * Every call is best-effort. Block Store is absent on a device without Play Services and can refuse
 * on one that has it, and neither is a reason to fail a sign-in or to strand the user on setup.
 */
class BlockStoreConnectionCarrier(
    private val client: BlockstoreClient,
) : ConnectionCarrier {
    override suspend fun put(credentials: SeerrCredentials) {
        val bytes = encodeCarriedConnection(credentials)
        if (bytes.size > BlockstoreClient.MAX_SIZE) return
        // Cloud backup only where Google holds no key to it. Without end-to-end encryption this is
        // a server-side secret in someone else's datacentre, so the entry stays on the device.
        val endToEnd = attempt { client.isEndToEndEncryptionAvailable().await() }.getOrDefault(false)
        val data =
            StoreBytesData
                .Builder()
                .setKey(CONNECTION_KEY)
                .setBytes(bytes)
                .setShouldBackupToCloud(endToEnd)
                .build()
        attempt { client.storeBytes(data).await() }
    }

    override suspend fun read(): SeerrCredentials? {
        val request = RetrieveBytesRequest.Builder().setKeys(listOf(CONNECTION_KEY)).build()
        val response = attempt { client.retrieveBytes(request).await() }.getOrNull() ?: return null
        val bytes = response.blockstoreDataMap[CONNECTION_KEY]?.bytes ?: return null
        return decodeCarriedConnection(bytes)
    }

    override suspend fun clear() {
        val request = DeleteBytesRequest.Builder().setKeys(listOf(CONNECTION_KEY)).build()
        attempt { client.deleteBytes(request).await() }
    }
}
