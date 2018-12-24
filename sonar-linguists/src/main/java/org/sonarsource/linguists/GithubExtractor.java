package org.sonarsource.linguists;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterable;

public class GithubExtractor {

  private static final String DATE_FORMAT = "yyyy-MM-dd";
  private static final Gson GSON = new GsonBuilder()
    .setPrettyPrinting()
    .setDateFormat(DATE_FORMAT)
    .create();

  private static final File RESULT_FOLDER = new File("target/results");
  private static final List<String> REPOSITORIES = Arrays.asList(
    "java-debugging-rules",
    "sonar-scanner-msbuild",
    "sonar-cpp",
    "sonar-html",
    "sonar-css",
    "sonar-vb",
    "sonar-pli",
    "sonar-dotnet",
    "sonarqube-roslyn-sdk",
    "sonar-rpg",
    "sonarqube-roslyn-sdk-template-plugin",
    "sonar-go",
    "slang-enterprise",
    "sonar-python",
    "SonarTS",
    "sonar-plsql",
    "sonar-xml",
    "sonar-tsql",
    "eslint-plugin-sonarjs",
    "sonar-lits",
    "sonar-analyzer-commons",
    "sonar-swift",
    "sonar-security",
    "sonar-cobol",
    "sonar-abap",
    "sonar-scanner-vsts",
    "sslr-squid-bridge",
    "source-graph-viewer",
    "sslr",
    "sonar-php",
    "SonarJS",
    "sonar-ucfg",
    "sonar-java"
    );

  public static void main(String[] args) throws Exception {
    new GithubExtractor("SonarSource").collect(REPOSITORIES).export(RESULT_FOLDER);
  }

  private final SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
  private final String organizationName;
  private final Map<String, List<Commit>> results = new HashMap<>();

  private GHOrganization organization;

  public GithubExtractor(String organizationName) {
    this.organizationName = organizationName;
  }

  private GithubExtractor collect(List<String> repositories) throws Exception {
    GitHub github = GitHub.connect("m-g-sonar", "...  my token ...");
    organization = github.getOrganization(organizationName);

    int counter = 0;

    for (String repositoryName : repositories) {
      System.out.println(repositoryName + ": START");
      GHRepository repository = organization.getRepository(repositoryName);
      PagedIterable<GHCommit> commits = repository.queryCommits().since(dateFormat.parse("2017-01-01")).list();
      System.out.println("----------");
      for (GHCommit commit : commits.asList()) {
        collectCommits(repositoryName, commit);
        counter++;
        if (counter % 10 == 0) {
          System.out.print("|");
          if (counter % 100 == 0) {
            System.out.println();
          }
        }
      }
      System.out.println();
      System.out.println("----------");
      System.out.println("----> #commits: " + counter);

      Map<String, Integer> commitsByUser = new HashMap<>();
      results.get(repositoryName).stream().forEach(commit -> commitsByUser.compute(commit.user, (user, n) -> (n == null) ? 1 : (n + 1)));

      System.out.println("----> #users:");
      commitsByUser.forEach((user, n) -> System.out.println(String.format("- %s (%s)", user, n)));

      System.out.println(repositoryName + ": DONE");
      System.out.println();
    }

    return this;
  }

  private void collectCommits(String repositoryName, GHCommit commit) throws Exception {
    GHUser author = commit.getAuthor();
    GHUser commiter = commit.getCommitter();

    if (Objects.equals(author, commiter)) {
      addIfMember(repositoryName, commit, author, Commit.Role.AUTHOR);
    } else {
      addIfMember(repositoryName, commit, author, Commit.Role.AUTHOR);
      addIfMember(repositoryName, commit, commiter, Commit.Role.COMMITTER);
    }
  }

  private void export(File resultFolder) throws Exception {
    System.out.println("Saving Results in: " + resultFolder.getPath());
    Files.deleteIfExists(resultFolder.toPath());
    resultFolder.mkdir();
    results.forEach((repo, commits) -> {
      String fileName = repo + ".json";
      try (FileOutputStream outputStream = new FileOutputStream(new File(resultFolder, fileName))) {
        byte[] strToBytes = GSON.toJson(commits).getBytes();
        outputStream.write(strToBytes);
      } catch (Exception e) {
        System.out.println("===================== Fail to write: " + fileName);
      }
    });
    System.out.println("Saving DONE");
  }

  private void addIfMember(String repositoryName, GHCommit commit, @Nullable GHUser user, Commit.Role role) throws Exception {
    if (user == null || !user.isMemberOf(organization)) {
      return;
    }
    String name = user.getName();
    if (name == null) {
      name = user.getLogin();
    }
    results.computeIfAbsent(repositoryName, key -> new ArrayList<>())
      .add(new Commit(repositoryName, name, role, commit.getCommitDate(), commit.getSHA1()));
  }

  private static class Commit {

    private enum Role {
      AUTHOR,
      COMMITTER;
    }

    final Role role;
    final String repository;
    final String user;
    final Date date;
    final String sha1;

    private Commit(String repository, String user, Role role, Date date, String sha1) {
      this.repository = repository;
      this.user = user;
      this.role = role;
      this.date = date;
      this.sha1 = sha1;
    }
  }

}
