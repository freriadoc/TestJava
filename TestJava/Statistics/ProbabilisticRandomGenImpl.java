package Statistics;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;


class ProbabilisticRandomGenImpl implements ProbabilisticRandomGen {

    private final List<NumAndProbability> numAndProbabilities;
    private final Random random;

    public ProbabilisticRandomGenImpl(List<NumAndProbability> numAndProbabilities) {
        this.numAndProbabilities = normalizeProbabilities(numAndProbabilities);
        this.random = new Random();
    }

    private List<NumAndProbability> normalizeProbabilities(List<NumAndProbability> numAndProbabilities) {
        double sumOfProbabilities = numAndProbabilities.stream().mapToDouble(NumAndProbability::getProbability).sum();
        return numAndProbabilities.stream().map(numAndProbability -> new NumAndProbability(numAndProbability.getNum(), numAndProbability.getProbability() / sumOfProbabilities)).collect(Collectors.toList());
    }

    @Override
    public int nextFromSample() {
        double randomValue = random.nextDouble();
        double cumulativeProbability = 0;

        for (NumAndProbability numAndProbability : numAndProbabilities) {
            cumulativeProbability += numAndProbability.getProbability();
            if (randomValue < cumulativeProbability) {
                return numAndProbability.getNum();
            }
        }

        throw new IllegalStateException("No number was found for random probability");
    }
}