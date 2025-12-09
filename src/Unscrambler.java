import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

public class Unscrambler {
    public static Mat unscramble(Mat scrambledFrame, int nbCols) {
        int rows = scrambledFrame.rows();

        int[] selectedCols = new int[nbCols];
        for (int i = 1; i < nbCols; i++) {
            selectedCols[i] = i * (scrambledFrame.cols() / nbCols+1);
        }

        byte[][] columnsData = new byte[nbCols][rows];

        for (int j = 0; j < nbCols; j++) {
            int col = selectedCols[j];
            for (int i = 0; i < rows; i++) {
                double[] pixel = scrambledFrame.get(i, col);
                columnsData[j][i] = (byte) pixel[0];
            }
        }

        double bestScore = Double.MAX_VALUE;
        int bestR = 0;
        int bestS = 0;

        for (int s = 0; s < 128; s++) {
            long stepFactor = 2L * s + 1L;

            for (int r = 0; r < 256; r++) {

                double currentScore = 0;
                for (int j = 0; j < nbCols; j++) {
                    int prevSrcIndex = -1;

                    int tempRow = 0;
                    while (tempRow < rows) {
                        int remaining = rows - tempRow;
                        int blockSize = Integer.highestOneBit(remaining);

                        for (int i = 0; i < blockSize; i++) {

                            long pos = (r + stepFactor * i) % blockSize;
                            int srcIndex = tempRow + (int) pos;

                            if (prevSrcIndex != -1) {
                                int val1 = columnsData[j][srcIndex] & 0xFF;
                                int val2 = columnsData[j][prevSrcIndex] & 0xFF;

                                currentScore += Math.abs(val1 - val2);
                            }
                            prevSrcIndex = srcIndex;
                        }
                        tempRow += blockSize;
                    }
                }

                if (currentScore < bestScore) {
                    bestScore = currentScore;
                    bestR = r;
                    bestS = s;
                }
            }
        }

        return unscrambleWithKeys(scrambledFrame, (byte) bestR, (byte) bestS);
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