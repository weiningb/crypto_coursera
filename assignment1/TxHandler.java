import java.util.HashSet;
import java.util.ArrayList;

public class TxHandler {

    private UTXOPool uPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        // IMPLEMENT THIS
        uPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        // IMPLEMENT THIS
        int numInputs = tx.numInputs();
        HashSet<UTXO> allUTXOInTx = new HashSet<UTXO>();
        int curr_UTXO_num = allUTXOInTx.size();
        int last_UTXO_num = curr_UTXO_num;
        double original_sum = 0;
        for (int i = 0; i < numInputs; i++) {
            Transaction.Input in = tx.getInput(i);
            UTXO curr_original_output = new UTXO(in.prevTxHash, in.outputIndex);
            if (!uPool.contains(curr_original_output))
                return false;
            Transaction.Output original_out = uPool.getTxOutput(curr_original_output);
            if (!Crypto.verifySignature(original_out.address,
                tx.getRawDataToSign(i), in.signature))
                return false;
            allUTXOInTx.add(curr_original_output);
            curr_UTXO_num = allUTXOInTx.size();
            if (last_UTXO_num == curr_UTXO_num)
                return false;
            last_UTXO_num = curr_UTXO_num;
            original_sum += original_out.value;
        }
        double output_sum = 0;
        for (Transaction.Output out : tx.getOutputs()) {
            if (out.value < 0)
                return false;
            output_sum += out.value;
        }
        if (original_sum < output_sum)
            return false;
        return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        // IMPLEMENT THIS
        ArrayList<Transaction> acceptedTxs = new ArrayList<Transaction>();
        int possibleTxsNum = possibleTxs.length;
        for (int i = 0; i < possibleTxsNum; i++) {
            Transaction currTrans = possibleTxs[i];
            if (isValidTx(currTrans)) {
                for (Transaction.Input in : currTrans.getInputs()) {
                    UTXO currOriginalOutput = new UTXO(in.prevTxHash, in.outputIndex);
                    uPool.removeUTXO(currOriginalOutput);
                }
                int currOutputNum = currTrans.getOutputs().size();
                for (int j = 0; j < currOutputNum; j++) {
                    UTXO currOutput = new UTXO(currTrans.getHash(), j);
                    uPool.addUTXO(currOutput, currTrans.getOutput(j));
                }
                acceptedTxs.add(possibleTxs[i]);
            }
        }
        return acceptedTxs.toArray(new Transaction[acceptedTxs.size()]);
    }

}
