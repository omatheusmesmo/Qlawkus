package dev.omatheusmesmo.qlawkus.cognition.review;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

/** Trusts the cosine distance, which is what the review did before a judge existed. */
@ApplicationScoped
@DefaultBean
public class PassThroughDuplicateJudge implements DuplicateJudge {

  @Override
  public boolean sameFact(String kept, String candidate) {
    return true;
  }
}
