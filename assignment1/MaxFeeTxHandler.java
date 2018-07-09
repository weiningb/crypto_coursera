import java.util.HashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.nio.ByteBuffer;
import java.util.LinkedList;

public class MaxFeeTxHandler {

    private UTXOPool uPool;
    // maxFee in this round of handle
    private double maxFee;
    // maxFeeTxs array in this round of handle
    private Transaction[] maxFeeTxs;
    // currTxs during the helper function
    private ArrayList<Transaction> currTxs;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public MaxFeeTxHandler(UTXOPool utxoPool) {
        // IMPLEMENT THIS
        uPool = new UTXOPool(utxoPool);
        maxFee = 0;
        maxFeeTxs = new Transaction[0];
        currTxs = new ArrayList<Transaction>();
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
     * Helper function to recursively calculate the max fee
     */
    private void handleTxsHelper(Transaction[] possibleTxs, int currId, double currFee) {
        int possibleTxsNum = possibleTxs.length;
        // all Txs are through, decide if the fee is max
        if (currId == possibleTxsNum) {
            if (currFee > maxFee) {
                maxFee = currFee;
                maxFeeTxs = currTxs.toArray(new Transaction[currTxs.size()]);
            }
            return;
        }

        for (int i = currId; i < possibleTxsNum; i++) {
            Transaction currTrans = possibleTxs[i];

            // temperal hashmap to store input to original output
            // do this only if current Tx is valid
            if (isValidTx(currTrans)) {

                currTxs.add(currTrans);
                double originalSum = 0;
                HashMap<UTXO, Transaction.Output> inputTmp = new HashMap<UTXO, Transaction.Output>();
                // remove all original outputs
                for (Transaction.Input in : currTrans.getInputs()) {
                    UTXO currOriginalOutput = new UTXO(in.prevTxHash, in.outputIndex);
                    Transaction.Output originalOut = uPool.getTxOutput(currOriginalOutput);
                    originalSum += originalOut.value;
                    inputTmp.put(currOriginalOutput, uPool.getTxOutput(currOriginalOutput));
                    uPool.removeUTXO(currOriginalOutput);
                }

                double outputSum = 0;
                // add new outputs
                int currOutputNum = currTrans.getOutputs().size();
                for (int j = 0; j < currOutputNum; j++) {
                    Transaction.Output currOut = currTrans.getOutput(j);
                    outputSum += currOut.value;
                    UTXO currOutput = new UTXO(currTrans.getHash(), j);
                    uPool.addUTXO(currOutput, currOut);
                }

                // update the fee and call next level
                double newFee = currFee + originalSum - outputSum;
                handleTxsHelper(possibleTxs, i+1, newFee);

                // revert all the changes caused by current Tx
                // add all the original outputs back
                for (Transaction.Input in : currTrans.getInputs()) {
                    UTXO currOriginalOutput = new UTXO(in.prevTxHash, in.outputIndex);
                    uPool.addUTXO(currOriginalOutput, inputTmp.get(currOriginalOutput));
                }

                // remove all the new outputs
                for (int j = 0; j < currOutputNum; j++) {
                    UTXO currOutput = new UTXO(currTrans.getHash(), j);
                    uPool.removeUTXO(currOutput);
                }

                // remove current Tx
                currTxs.remove(currTxs.size()-1);
            }
        }
    }

    /**
     * Sort the txs according to dependency, tx w/o dependencies will come first
     */

    private Transaction[] topoSort(Transaction[] txs) {
        ArrayList<Transaction> sorted = new ArrayList<Transaction>();
        HashSet<ByteBuffer> allHashes = new HashSet<ByteBuffer>();
        HashSet<Transaction> allTrans = new HashSet<Transaction>();
        int txsNum = txs.length;
        for (int i = 0; i < txsNum; i++) {
            allHashes.add(ByteBuffer.wrap(txs[i].getHash()));
            allTrans.add(txs[i]);
        }
        LinkedList<Transaction> txsWithoutDepend = new LinkedList<Transaction>();
        for (int i = 0; i < txsNum; i++) {
            Transaction currTrans = txs[i];
            boolean hasDepend = false;
            for (Transaction.Input in : currTrans.getInputs()) {
                hasDepend = (hasDepend || allHashes.contains(ByteBuffer.wrap(in.prevTxHash)));
            }
            if (!hasDepend) {
                txsWithoutDepend.add(currTrans);
                allTrans.remove(currTrans);
            }
        }
        while (txsWithoutDepend.size() > 0) {
            Transaction currHead = txsWithoutDepend.getFirst();
            allHashes.remove(ByteBuffer.wrap(currHead.getHash()));
            for (Transaction currTrans : allTrans) {
                boolean hasDepend = false;
                for (Transaction.Input in : currTrans.getInputs()) {
                    hasDepend = (hasDepend || allHashes.contains(ByteBuffer.wrap(in.prevTxHash)));
                }
                if (!hasDepend) {
                    txsWithoutDepend.add(currTrans);
                    allTrans.remove(currTrans);
                }
            }
            sorted.add(currHead);
        }
        return sorted.toArray(new Transaction[sorted.size()]);
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        // IMPLEMENT THIS
        maxFee = 0;
        maxFeeTxs = new Transaction[0];
        currTxs = new ArrayList<Transaction>();

        // topo sort so that no dependency tx always comes first
        //Transaction[] sortedTxs = topoSort(possibleTxs);

        handleTxsHelper(possibleTxs, 0, 0);

        // update uPool according to the maxFeeTxs
        int maxFeeTxsNum = maxFeeTxs.length;
        for (int i = 0; i < maxFeeTxsNum; i++) {
            Transaction currTrans = maxFeeTxs[i];
            for (Transaction.Input in : currTrans.getInputs()) {
                UTXO currOriginalOutput = new UTXO(in.prevTxHash, in.outputIndex);
                uPool.removeUTXO(currOriginalOutput);
            }
            int currOutputNum = currTrans.getOutputs().size();
            for (int j = 0; j < currOutputNum; j++) {
                UTXO currOutput = new UTXO(currTrans.getHash(), j);
                uPool.addUTXO(currOutput, currTrans.getOutput(j));
            }
        }

        return maxFeeTxs;
    }

}
