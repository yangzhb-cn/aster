package com.aster.app.rag.retrieve;

import java.util.List;

/**
 * 余弦相似度计算。
 */
public final class CosineSimilarity {
    private CosineSimilarity() {
    }

    /**
     * 计算两个向量的余弦相似度。
     */
    public static double score(List<Double> left, List<Double> right) {
        if (left == null || right == null || left.isEmpty() || left.size() != right.size()) {
            return Double.NEGATIVE_INFINITY;
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.size(); i++) {
            double l = left.get(i);
            double r = right.get(i);
            dot += l * r;
            leftNorm += l * l;
            rightNorm += r * r;
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return Double.NEGATIVE_INFINITY;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
