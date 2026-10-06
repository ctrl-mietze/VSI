package com.aefyr.sai.utils;

import com.aefyr.sai.installerx.common.SplitApkSourceMeta;
import com.aefyr.sai.installerx.common.SplitPart;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class VsiPackageValidator {

    private VsiPackageValidator() {}

    public static Result validate(SplitApkSourceMeta meta) {
        ArrayList<String> problems = new ArrayList<>();
        ArrayList<String> warnings = new ArrayList<>();

        if (meta == null) {
            problems.add("Package metadata is missing.");
            return new Result(problems, warnings);
        }

        if (meta.appMeta() == null || meta.appMeta().packageName == null
                || meta.appMeta().packageName.trim().isEmpty()) {
            problems.add("Base package metadata or package name is missing.");
        }

        Set<String> paths = new HashSet<>();
        List<SplitPart> allParts = new ArrayList<>();
        allParts.addAll(meta.flatSplits());
        allParts.addAll(meta.hiddenSplits());

        for (SplitPart part : allParts) {
            String path = part.localPath();
            if (path == null || path.trim().isEmpty()) {
                problems.add("A split has no local path.");
                continue;
            }

            if (!paths.add(path))
                problems.add("Duplicate package entry: " + path);

            if (part.size() == 0)
                problems.add("Empty APK entry: " + path);
            else if (part.size() < 0)
                warnings.add("Unknown size: " + path);
        }

        if (allParts.isEmpty())
            warnings.add("No explicit split entries were exposed by the resolver.");

        return new Result(problems, warnings);
    }

    public static final class Result {
        public final List<String> problems;
        public final List<String> warnings;

        Result(List<String> problems, List<String> warnings) {
            this.problems = problems;
            this.warnings = warnings;
        }

        public boolean isValid() {
            return problems.isEmpty();
        }

        public String describe() {
            StringBuilder sb = new StringBuilder();
            for (String problem : problems)
                sb.append("• ").append(problem).append("\n");
            for (String warning : warnings)
                sb.append("• ").append(warning).append("\n");
            return sb.toString().trim();
        }
    }
}
