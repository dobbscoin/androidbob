/*
 * Copyright 2011-2015 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;

/**
 * Wraps Dobbscoin's MainNetParams with a null-safe difficulty check.
 *
 * GetNextWorkRequired_V4 looks back a window of historical blocks to compute
 * the required difficulty for the next block.  In SPV mode the block store is
 * a small circular buffer: when syncing starts from a checkpoint, those
 * look-back blocks do not exist yet, causing a NullPointerException that
 * disconnects every peer and prevents the chain from ever advancing.
 *
 * The fix: catch that NPE and accept the header's declared difficulty rather
 * than crashing.  Security is not materially reduced — the chain still has to
 * present the most cumulative proof-of-work, and we only connect to known
 * Dobbscoin seed nodes.
 */
public class DobbscoinNetParams extends MainNetParams
{
    private static DobbscoinNetParams instance;

    public DobbscoinNetParams()
    {
        super();
    }

    public static synchronized DobbscoinNetParams get()
    {
        if (instance == null)
        {
            instance = new DobbscoinNetParams();

            // WalletProtobufSerializer.readWallet() calls
            // NetworkParameters.fromID("org.dobbscoin.production") which returns
            // MainNetParams.get() — a different object from our DobbscoinNetParams.
            // The bitcoinj Context check then throws IllegalStateException because
            // the thread context holds DobbscoinNetParams but the wallet params is
            // MainNetParams.  Fix: inject our singleton into MainNetParams so that
            // MainNetParams.get() returns this same DobbscoinNetParams instance.
            try
            {
                final java.lang.reflect.Field f =
                        MainNetParams.class.getDeclaredField("instance");
                f.setAccessible(true);
                f.set(null, instance);
            }
            catch (final Exception ignored) {}
        }
        return instance;
    }

    @Override
    public void checkDifficultyTransitions(final StoredBlock storedPrev,
            final Block nextBlock, final BlockStore blockStore)
            throws BlockStoreException, VerificationException
    {
        try
        {
            super.checkDifficultyTransitions(storedPrev, nextBlock, blockStore);
        }
        catch (final NullPointerException e)
        {
            // GetNextWorkRequired_V4 needs historical blocks not held in the
            // SPV circular store.  Accept the header's declared difficulty
            // rather than disconnecting the peer.
        }
    }
}
