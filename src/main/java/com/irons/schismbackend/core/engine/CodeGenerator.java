package com.irons.schismbackend.core.engine;

import com.irons.schismbackend.core.model.GameCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CodeGenerator {

    public static GameCode generateMask() {

        List<Integer> pool = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6));
        Collections.shuffle(pool);

        List<Integer> selectedDigits = pool.subList(0, 4);

        return new GameCode(selectedDigits);
    }
}
