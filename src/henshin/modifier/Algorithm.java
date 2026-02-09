package henshin.modifier;

import org.eclipse.emf.henshin.model.Rule;

public interface Algorithm {
    void apply(Rule rule) throws Exception;
}
