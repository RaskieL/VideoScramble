import org.opencv.core.Mat;

public class Scrambler {
    public static Mat scramble(Mat frame, byte r, byte s) {
        if (frame.empty()) return new Mat();
        Mat scrambledImage = new Mat(frame.rows(), frame.cols(), frame.type());

        int totalRows = frame.rows();
        int currentRow = 0;

        while (currentRow < totalRows) {
            int remainingRows = totalRows - currentRow;

            int blockSize = Integer.highestOneBit(remainingRows);

            long stepFactor = 2L * (s & 0xFF) + 1L;
            int offset = (r & 0xFF);

            for (int i = 0; i < blockSize; i++) {
                long newPosRelative = (offset + stepFactor * i) % blockSize;

                int sourceRowIdx = currentRow + i;
                int destRowIdx = currentRow + (int) newPosRelative;

                Mat sourceRow = frame.row(sourceRowIdx);
                Mat destRow = scrambledImage.row(destRowIdx);
                sourceRow.copyTo(destRow);
            }

            currentRow += blockSize;
        }

        return scrambledImage;
    }
}