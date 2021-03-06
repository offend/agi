/*
 * Copyright (C) 2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.gapid.perfetto.views;

import static com.google.gapid.perfetto.views.Loading.drawLoading;
import static com.google.gapid.perfetto.views.StyleConstants.TRACK_MARGIN;
import static com.google.gapid.perfetto.views.StyleConstants.colors;
import static com.google.gapid.perfetto.views.StyleConstants.mainGradient;
import static com.google.gapid.util.MoreFutures.transform;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;

import com.google.gapid.perfetto.TimeSpan;
import com.google.gapid.perfetto.canvas.Area;
import com.google.gapid.perfetto.canvas.Fonts;
import com.google.gapid.perfetto.canvas.RenderContext;
import com.google.gapid.perfetto.canvas.Size;
import com.google.gapid.perfetto.models.CounterInfo;
import com.google.gapid.perfetto.models.CounterTrack;
import com.google.gapid.perfetto.models.CounterTrack.Values;
import com.google.gapid.perfetto.models.Selection;
import com.google.gapid.perfetto.models.Selection.CombiningBuilder;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.widgets.Display;

import java.util.List;

public class CounterPanel extends TrackPanel<CounterPanel> implements Selectable {
  private static final double HOVER_MARGIN = 10;
  private static final double HOVER_PADDING = 4;
  private static final double CURSOR_SIZE = 5;

  protected final CounterTrack track;
  protected final double trackHeight;
  protected HoverCard hovered = null;

  public CounterPanel(State state, CounterTrack track, double trackHeight) {
    super(state);
    this.track = track;
    this.trackHeight = trackHeight;
  }

  @Override
  public CounterPanel copy() {
    return new CounterPanel(state, track, trackHeight);
  }

  @Override
  public String getTitle() {
    CounterInfo info = track.getCounter();
    if (info.type == CounterInfo.Type.Gpu && "gpufreq".equals(info.name)) {
      return "GPU " + info.ref + " Frequency";
    }
    return track.getCounter().name;
  }

  @Override
  public String getTooltip() {
    CounterInfo counter = track.getCounter();
    StringBuilder sb = new StringBuilder().append("\\b").append(counter.name);
    if (!counter.description.isEmpty()) {
      sb.append("\n").append(counter.description);
    }
    return sb.toString();
  }

  @Override
  public double getHeight() {
    return trackHeight;
  }

  @Override
  protected void renderTrack(RenderContext ctx, Repainter repainter, double w, double h) {
    ctx.trace("Counter", () -> {
      CounterTrack.Data data = track.getData(state.toRequest(), onUiThread(repainter));
      drawLoading(ctx, data, state, h);

      if (data == null || data.ts.length == 0) {
        return;
      }

      CounterInfo counter = track.getCounter();
      double min = counter.range.min, range = counter.range.range();

      Selection<?> selected = state.getSelection(Selection.Kind.Counter);
      List<Integer> visibleSelected = Lists.newArrayList();
      mainGradient().applyBaseAndBorder(ctx);
      ctx.path(path -> {
        double lastX = state.timeToPx(data.ts[0]), lastY = h;
        path.moveTo(lastX, lastY);
        for (int i = 0; i < data.ts.length; i++) {
          double nextX = state.timeToPx(data.ts[i]);
          double nextY = (trackHeight - 1) * (1 - (data.values[i] - min) / range);
          path.lineTo(nextX, lastY);
          path.lineTo(nextX, nextY);
          lastX = nextX;
          lastY = nextY;
          if (selected.contains(data.ids[i])) {
            visibleSelected.add(i);
          }
        }
        path.lineTo(lastX, h);
        ctx.fillPath(path);
        ctx.drawPath(path);
      });

      // Draw highlight line after the whole graph is rendered, so that the highlight is on the top.
      ctx.setBackgroundColor(mainGradient().highlight);
      for (int index : visibleSelected) {
        double startX = state.timeToPx(data.ts[index]);
        double endX = (index >= data.ts.length - 1) ? startX : state.timeToPx(data.ts[index + 1]);
        double y = (trackHeight - 1) * (1 - (data.values[index] - min) / range);
        ctx.fillRect(startX, y - 1, endX - startX, 3);
      }

      if (hovered != null) {
        double y = (trackHeight - 1) * (1 - (hovered.value - min) / range);
        ctx.setBackgroundColor(mainGradient().highlight);
        ctx.fillRect(hovered.startX, y - 1, hovered.endX - hovered.startX, trackHeight - y + 1);
        ctx.setForegroundColor(colors().textMain);
        ctx.drawCircle(hovered.mouseX, y, CURSOR_SIZE / 2);

        ctx.setBackgroundColor(colors().hoverBackground);
        double bgH = Math.max(hovered.size.h, trackHeight);
        double x = Math.min(hovered.getTooltipX(), w - (2 * HOVER_PADDING + hovered.size.w));
        ctx.fillRect(x, Math.min((trackHeight - bgH) / 2, 0),
            2 * HOVER_PADDING + hovered.size.w, bgH);
        ctx.setForegroundColor(colors().textMain);
        x += HOVER_PADDING;
        y = (trackHeight - hovered.size.h) / 2;
        double dx = hovered.leftWidth + HOVER_PADDING, dy = hovered.size.h / 2;
        ctx.drawText(Fonts.Style.Normal, "Value:", x, y);
        ctx.drawText(Fonts.Style.Normal, "Avg:", x, y + dy);
        ctx.drawText(Fonts.Style.Normal, "Min:", x + dx, y);
        ctx.drawText(Fonts.Style.Normal, "Max:", x + dx, y + dy);

        x += hovered.leftWidth;
        ctx.drawTextRightJustified(Fonts.Style.Normal, hovered.label, x, y, dy);
        ctx.drawTextRightJustified(Fonts.Style.Normal, hovered.avg, x, y + dy, dy);
        x = Math.min(hovered.getTooltipX() + HOVER_PADDING + hovered.size.w, w - HOVER_PADDING);
        ctx.drawTextRightJustified(Fonts.Style.Normal, hovered.min, x, y, dy);
        ctx.drawTextRightJustified(Fonts.Style.Normal, hovered.max, x, y + dy, dy);
      }
    });
  }

  @Override
  protected Hover onTrackMouseMove(Fonts.TextMeasurer m, double x, double y, int mods) {
    CounterTrack.Data data = track.getData(state.toRequest(), onUiThread());
    if (data == null || data.ts.length == 0) {
      return Hover.NONE;
    }

    long time = state.pxToTime(x);
    if (time < data.ts[0] || time > data.ts[data.ts.length - 1]) {
      return Hover.NONE;
    }

    int idx = 0;
    for (; idx < data.ts.length - 1; idx++) {
      if (data.ts[idx + 1] > time) {
        break;
      }
    }

    if (idx >= data.ts.length) {
      return Hover.NONE;
    }

    long id = data.ids[idx];
    double startX = state.timeToPx(data.ts[idx]);
    double endX = (idx >= data.ts.length - 1) ? startX : state.timeToPx(data.ts[idx + 1]);
    hovered = new HoverCard(m, track.getCounter(), data.values[idx], startX, endX, x);

    return new Hover() {
      @Override
      public Area getRedraw() {
        double start = Math.min(hovered.mouseX - CURSOR_SIZE / 2, startX);
        start = Math.min(start, state.getWidth() - hovered.size.w - 2 * HOVER_PADDING);
        double end = Math.max(
            hovered.getTooltipX() + HOVER_PADDING + hovered.size.w + HOVER_PADDING,
            endX);
        return new Area(start, -TRACK_MARGIN, end - start, trackHeight + 2 * TRACK_MARGIN);
      }

      @Override
      public void stop() {
        hovered = null;
      }

      @Override
      public Cursor getCursor(Display display) {
        return display.getSystemCursor(SWT.CURSOR_HAND);
      }

      @Override
      public boolean click() {
        if ((mods & SWT.MOD1) == SWT.MOD1) {
          state.addSelection(Selection.Kind.Counter,
              transform(track.getValue(id), d -> new CounterTrack.Values(track.getCounter().name, d)));
        } else {
          state.setSelection(Selection.Kind.Counter,
              transform(track.getValue(id), d -> new CounterTrack.Values(track.getCounter().name, d)));
        }
        return true;
      }
    };
  }

  @Override
  public void computeSelection(CombiningBuilder builder, Area area, TimeSpan ts) {
    builder.add(Selection.Kind.Counter, (ListenableFuture<Values>)transform(track.getValues(ts),
        data -> new CounterTrack.Values(track.getCounter().name, data)));
  }

  private static class HoverCard {
    public final double value;
    public final double startX, endX;
    public final double mouseX;
    public final String label;
    public final String min, max, avg;
    public final double leftWidth;
    public final Size size;

    public HoverCard(Fonts.TextMeasurer tm, CounterInfo counter, double value, double startX,
        double endX, double mouseX) {
      this.value = value;
      this.startX = startX;
      this.endX = endX;
      this.mouseX = mouseX;
      this.label = counter.unit.format(value);
      this.min = counter.unit.format(counter.min);
      this.max = counter.unit.format(counter.max);
      this.avg = counter.unit.format(counter.avg);

      Size valueSize = tm.measure(Fonts.Style.Normal, label);
      Size minSize = tm.measure(Fonts.Style.Normal, min);
      Size maxSize = tm.measure(Fonts.Style.Normal, max);
      Size avgSize = tm.measure(Fonts.Style.Normal, avg);

      double leftLabel = Math.max(
          tm.measure(Fonts.Style.Normal, "Value:").w,
          tm.measure(Fonts.Style.Normal, "Min:").w) + HOVER_PADDING;
      double rightLabel = Math.max(
          tm.measure(Fonts.Style.Normal, "Max:").w,
          tm.measure(Fonts.Style.Normal, "Avg:").w) + HOVER_PADDING;
      this.leftWidth = leftLabel + Math.max(valueSize.w, avgSize.w);
      this.size = new Size(leftWidth + HOVER_PADDING + rightLabel + Math.max(minSize.w, maxSize.w),
          Math.max(valueSize.h + avgSize.h, minSize.h + maxSize.h) + HOVER_PADDING);
    }

    public double getTooltipX() {
      // If the sample is smaller than the tooltip, show the tooltip after the sample.
      return ((size.w < endX - startX) ? mouseX + CURSOR_SIZE / 2 : endX) + HOVER_MARGIN;
    }
  }
}
