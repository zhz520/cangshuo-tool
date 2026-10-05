package com.cangshuo.toolbox.feedback.service;

import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import com.cangshuo.toolbox.feedback.model.FeedbackRequest;
import com.cangshuo.toolbox.feedback.model.FeedbackResponse;
import com.cangshuo.toolbox.feedback.repository.FeedbackRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FeedbackService {
    private final FeedbackRepository feedback;
    private final FeedbackInput input;

    public FeedbackService(FeedbackRepository feedback, FeedbackInput input) {
        this.feedback = feedback; this.input = input;
    }

    public FeedbackResponse submit(long userId, FeedbackRequest request) {
        var normalized = input.normalize(request.type(), request.content(), request.contact());
        long id = feedback.create(userId, normalized.type(), normalized.content(), normalized.contact());
        return feedback.findById(userId, id).orElseThrow(() -> new ApiException(ApiError.INTERNAL_ERROR));
    }

    public List<FeedbackResponse> mine(long userId) { return feedback.listByUser(userId); }
}
