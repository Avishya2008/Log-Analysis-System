import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public class AhoCorasick {

    private static class Node {

        Map<Character, Integer> children = new HashMap<>();

        int failureLink = 0;

        List<String> output = new ArrayList<>();
    }

    private List<Node> nodes = new ArrayList<>();

    public AhoCorasick(List<String> patterns) {

        nodes.add(new Node());

        for (String pattern : patterns) {
            addPattern(pattern);
        }

        buildFailureLinks();
    }

    private void addPattern(String pattern) {

        int current = 0;

        String lowerPattern = pattern.toLowerCase();

        for (char c : lowerPattern.toCharArray()) {

            if (!nodes.get(current).children.containsKey(c)) {

                nodes.get(current).children.put(
                        c,
                        nodes.size()
                );

                nodes.add(new Node());
            }

            current =
                    nodes.get(current).children.get(c);
        }

        nodes.get(current).output.add(pattern);
    }

    private void buildFailureLinks() {

        Queue<Integer> queue = new LinkedList<>();

        for (int child :
                nodes.get(0).children.values()) {

            nodes.get(child).failureLink = 0;

            queue.add(child);
        }

        while (!queue.isEmpty()) {

            int current = queue.poll();

            for (Map.Entry<Character, Integer> entry :
                    nodes.get(current).children.entrySet()) {

                char character = entry.getKey();

                int child = entry.getValue();

                queue.add(child);

                int failure =
                        nodes.get(current).failureLink;

                while (failure != 0 &&
                        !nodes.get(failure)
                                .children
                                .containsKey(character)) {

                    failure =
                            nodes.get(failure).failureLink;
                }

                if (nodes.get(failure)
                        .children
                        .containsKey(character)
                        &&
                        nodes.get(failure)
                                .children
                                .get(character) != child) {

                    nodes.get(child).failureLink =
                            nodes.get(failure)
                                    .children
                                    .get(character);

                } else {

                    nodes.get(child).failureLink = 0;
                }

                nodes.get(child).output.addAll(
                        nodes.get(
                                nodes.get(child).failureLink
                        ).output
                );
            }
        }
    }

    public List<String> search(String text) {

        List<String> matches =
                new ArrayList<>();

        int current = 0;

        String lowerText =
                text.toLowerCase();

        for (char character :
                lowerText.toCharArray()) {

            while (current != 0 &&
                    !nodes.get(current)
                            .children
                            .containsKey(character)) {

                current =
                        nodes.get(current).failureLink;
            }

            if (nodes.get(current)
                    .children
                    .containsKey(character)) {

                current =
                        nodes.get(current)
                                .children
                                .get(character);

            } else {

                current = 0;
            }

            if (!nodes.get(current)
                    .output
                    .isEmpty()) {

                matches.addAll(
                        nodes.get(current).output
                );
            }
        }

        return matches;
    }
}