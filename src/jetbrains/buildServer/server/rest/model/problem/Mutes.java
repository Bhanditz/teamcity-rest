package jetbrains.buildServer.server.rest.model.problem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.model.Href;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.mute.MuteInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 16.11.13
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "mutes")
public class Mutes {
  @XmlAttribute public long count;
  @XmlAttribute(required = false) @Nullable public String nextHref;
  @XmlAttribute(required = false) @Nullable public String prevHref;
  @XmlAttribute(name = "href") public String href;

  @XmlElement(name = "mute") public List<Mute> items;

  public Mutes() {
  }

  public Mutes(@NotNull final Collection<MuteInfo> itemsP,
               @Nullable final Href hrefP, //todo: not nulls are not yet implemented
               @Nullable final PagerData pagerData,
               @NotNull final BeanContext beanContext) {
    items = new ArrayList<Mute>(itemsP.size()); //todo: consider adding ordering/sorting
    for (MuteInfo item : itemsP) {
      items.add(new Mute(item, beanContext));
    }
    href = hrefP !=  null ? hrefP.getHref() : null;
    if (pagerData != null) {
      nextHref = pagerData.getNextHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getNextHref()) : null;
      prevHref = pagerData.getPrevHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getPrevHref()) : null;
    }
    count = items.size();
  }
}
