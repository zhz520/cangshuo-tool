package com.cangshuo.toolbox.zepp;

import com.cangshuo.toolbox.common.web.JsonBodyBudgetFilter;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Existing Zepp byte budget, shared with the media parser. */
class ZeppRequestBudgetFilter extends JsonBodyBudgetFilter {
    static final int MAX_BYTES = 4096;
    ZeppRequestBudgetFilter(ObjectMapper mapper) { super(mapper, MAX_BYTES); }
}
