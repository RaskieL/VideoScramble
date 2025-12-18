import org.opencv.core.*;

public class Unscrambler {

    public static Mat unscramble(Mat scrambledFrame, int nbCols) {
        int rows = scrambledFrame.rows();
        int[] selectedCols = selectColumnIndices(scrambledFrame.cols(), nbCols);
        byte[][] columnsData = extractColumnsData(scrambledFrame, selectedCols, rows);

        int[] bestKeys = findBestKeys(columnsData, rows, nbCols);

        return unscrambleWithKeys(scrambledFrame, (byte) bestKeys[0], (byte) bestKeys[1]);
    }

    /**
     * Sélectionne les indices de colonnes à échantillonner dans l'image.
     */
    private static int[] selectColumnIndices(int totalCols, int nbCols) {
        int[] selectedCols = new int[nbCols];
        for (int i = 1; i < nbCols; i++) {
            selectedCols[i] = i * (totalCols / nbCols + 1);
        }
        return selectedCols;
    }

    /**
     * Extrait les données des colonnes sélectionnées.
     */
    private static byte[][] extractColumnsData(Mat frame, int[] selectedCols, int rows) {
        int nbCols = selectedCols.length;
        byte[][] columnsData = new byte[nbCols][rows];

        for (int j = 0; j < nbCols; j++) {
            int col = selectedCols[j];
            for (int i = 0; i < rows; i++) {
                double[] pixel = frame.get(i, col);
                columnsData[j][i] = (byte) pixel[0];
            }
        }
        return columnsData;
    }

    /**
     * Trouve les meilleures clés R et S en testant toutes les combinaisons possibles.
     */
    private static int[] findBestKeys(byte[][] columnsData, int rows, int nbCols) {
        double bestScore = Double.MAX_VALUE;
        int bestR = 0;
        int bestS = 0;

        for (int s = 0; s < 128; s++) {
            for (int r = 0; r < 256; r++) {
                double currentScore = calculateScoreForKeys(r, s, columnsData, rows, nbCols);

                if (currentScore < bestScore) {
                    bestScore = currentScore;
                    bestR = r;
                    bestS = s;
                }
            }
        }

        return new int[]{bestR, bestS};
    }

    /**
     * Calcule le score de rugosité pour une combinaison de clés R et S donnée.
     */
    private static double calculateScoreForKeys(int r, int s, byte[][] columnsData, int rows, int nbCols) {
        long stepFactor = 2L * s + 1L;
        double totalScore = 0;

        for (int j = 0; j < nbCols; j++) {
            totalScore += calculateColumnScore(r, stepFactor, columnsData[j], rows);
        }

        return totalScore;
    }

    /**
     * Calcule le score pour une seule colonne.
     */
    private static double calculateColumnScore(int r, long stepFactor, byte[] columnData, int rows) {
        double score = 0;
        int prevSrcIndex = -1;
        int tempRow = 0;

        while (tempRow < rows) {
            int remaining = rows - tempRow;
            int blockSize = Integer.highestOneBit(remaining);

            for (int i = 0; i < blockSize; i++) {
                long pos = (r + stepFactor * i) % blockSize;
                int srcIndex = tempRow + (int) pos;

                if (prevSrcIndex != -1) {
                    int val1 = columnData[srcIndex] & 0xFF;
                    int val2 = columnData[prevSrcIndex] & 0xFF;
                    score += Math.abs(val1 - val2);
                }
                prevSrcIndex = srcIndex;
            }
            tempRow += blockSize;
        }

        return score;
    }

    /**
     * Déchiffre une image avec des clés connues R et S.
     */
    public static Mat unscrambleWithKeys(Mat frame, byte r, byte s) {
        if (frame.empty()) return new Mat();
        Mat result = new Mat(frame.rows(), frame.cols(), frame.type());
        unscrambleToDst(frame, result, r, s);
        return result;
    }

    /**
     * Logique interne de déchiffrement.
     * Utilise la MEME formule que le brouilleur pour retrouver les indices.
     */
    private static void unscrambleToDst(Mat source, Mat destination, byte r, byte s) {
        if (destination.empty() || destination.rows() != source.rows() || destination.cols() != source.cols()) {
            destination.create(source.rows(), source.cols(), source.type());
        }

        int totalRows = source.rows();
        int currentRow = 0;

        while (currentRow < totalRows) {
            int remainingRows = totalRows - currentRow;
            int blockSize = Integer.highestOneBit(remainingRows);

            long stepFactor = 2L * (s & 0xFF) + 1L;
            int offset = (r & 0xFF);

            for (int i = 0; i < blockSize; i++) {
                long scrambledPosRelative = (offset + stepFactor * i) % blockSize;
                int srcRowIdx = currentRow + (int) scrambledPosRelative;
                int destRowIdx = currentRow + i;

                Mat srcRow = source.row(srcRowIdx);
                Mat dstRow = destination.row(destRowIdx);
                srcRow.copyTo(dstRow);
            }
            currentRow += blockSize;
        }
    }

    /**
     * Calcule un score de "rugosité".
     * Une image normale a peu de différences brutales entre la ligne N et N+1.
     * Une image mal déchiffrée est très hachée.
     * Plus le score est bas, mieux c'est.
     */
    public static double calculateRoughnessScore(Mat img) {
        int colToTest = img.cols() / 2;

        double totalDiff = 0;

        for (int i = 0; i < img.rows() - 1; i++) {
            double[] pixelA = img.get(i, colToTest);
            double[] pixelB = img.get(i + 1, colToTest);

            for(int c=0; c<pixelA.length; c++) {
                totalDiff += Math.abs(pixelA[c] - pixelB[c]);
            }
        }
        return totalDiff;
    }
}