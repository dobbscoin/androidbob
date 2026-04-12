package finance.subgenius.dobbscoin;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import java.util.ArrayList;
import java.util.List;

public final class TransactionSigner {
    private final WalletManager walletManager;
    private final NetworkParameters networkParameters = MainNetParams.get();

    public TransactionSigner(WalletManager walletManager) {
        this.walletManager = walletManager;
    }

    public TransactionPlan createTransaction(
        List<WalletInput> availableInputs,
        String destinationAddress,
        long amountSatoshis,
        long feeSatoshis,
        String changeAddress
    ) {
        if (amountSatoshis <= 0L) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (feeSatoshis < 0L) {
            throw new IllegalArgumentException("Fee cannot be negative");
        }

        long required = amountSatoshis + feeSatoshis;
        long selectedTotal = 0L;
        List<WalletInput> selectedInputs = new ArrayList<>();
        for (WalletInput input : availableInputs) {
            selectedInputs.add(input);
            selectedTotal += input.valueSatoshis;
            if (selectedTotal >= required) {
                break;
            }
        }
        if (selectedTotal < required) {
            throw new IllegalStateException("Insufficient deterministic inputs for local signing");
        }

        Transaction transaction = new Transaction(networkParameters);
        transaction.addOutput(Coin.valueOf(amountSatoshis), LegacyAddress.fromBase58(networkParameters, destinationAddress));
        long changeSatoshis = selectedTotal - required;
        if (changeSatoshis > 0L) {
            transaction.addOutput(Coin.valueOf(changeSatoshis), LegacyAddress.fromBase58(networkParameters, changeAddress));
        }

        for (WalletInput input : selectedInputs) {
            Script scriptPubKey = ScriptBuilder.createOutputScript(
                LegacyAddress.fromBase58(networkParameters, input.address)
            );
            transaction.addInput(Sha256Hash.wrap(input.transactionId), input.outputIndex, scriptPubKey);
        }

        return new TransactionPlan(transaction, selectedInputs);
    }

    public TransactionPlan signTransaction(TransactionPlan plan) {
        for (int inputIndex = 0; inputIndex < plan.inputs.size(); inputIndex++) {
            WalletInput input = plan.inputs.get(inputIndex);
            DeterministicKey key = walletManager.getKeyForIndex(input.derivationIndex);
            Script scriptPubKey = ScriptBuilder.createOutputScript(
                LegacyAddress.fromBase58(networkParameters, input.address)
            );
            TransactionSignature signature = plan.transaction.calculateSignature(
                inputIndex,
                key,
                scriptPubKey,
                Transaction.SigHash.ALL,
                false
            );
            plan.transaction.getInput(inputIndex).setScriptSig(ScriptBuilder.createInputScript(signature, key));
        }
        return plan;
    }

    public String serializeTransaction(TransactionPlan plan) {
        return org.bitcoinj.core.Utils.HEX.encode(plan.transaction.bitcoinSerialize());
    }

    public static final class WalletInput {
        public final String transactionId;
        public final long outputIndex;
        public final long valueSatoshis;
        public final String address;
        public final int derivationIndex;

        public WalletInput(String transactionId, long outputIndex, long valueSatoshis, String address, int derivationIndex) {
            this.transactionId = transactionId;
            this.outputIndex = outputIndex;
            this.valueSatoshis = valueSatoshis;
            this.address = address;
            this.derivationIndex = derivationIndex;
        }
    }

    public static final class TransactionPlan {
        public final Transaction transaction;
        public final List<WalletInput> inputs;

        TransactionPlan(Transaction transaction, List<WalletInput> inputs) {
            this.transaction = transaction;
            this.inputs = inputs;
        }
    }
}
