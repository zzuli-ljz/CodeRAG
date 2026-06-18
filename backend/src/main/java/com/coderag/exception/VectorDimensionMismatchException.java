package com.coderag.exception;

/**
 * 向量维度不匹配异常
 * 当数据库中存储的向量维度与当前查询向量维度不一致时抛出
 */
public class VectorDimensionMismatchException extends RuntimeException {

    public VectorDimensionMismatchException(String message) {
        super(message);
    }
}
